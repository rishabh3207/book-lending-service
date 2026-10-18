# Book Lending Service

[![CI](https://github.com/rishabh3207/book-lending-service/actions/workflows/ci.yml/badge.svg)](https://github.com/rishabh3207/book-lending-service/actions/workflows/ci.yml)

Spring Boot microservice for managing a library catalog and members, and for borrowing and
returning books under configurable lending rules.

**Stack:** Java 25, Spring Boot 4, Spring Data JPA, PostgreSQL 16, Flyway, Spring Security,
springdoc-openapi, JUnit 5, Testcontainers, Docker Compose.

## Running

Requires Docker.

```bash
docker compose up --build
```

- API: http://localhost:8080/api
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

Run the app without Docker (Postgres still in Docker):

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

If an older local database fails Flyway validation, reset it with `docker compose down -v`.

## Users

| Username | Password | Role |
|---|---|---|
| librarian | librarian123 | LIBRARIAN |
| rishabh@gmail.com | password123 | MEMBER (id 1) |
| shivamgarg@gmail.com | password123 | MEMBER (id 2) |

HTTP Basic auth; users are defined (BCrypt-hashed) in `application.yml`. A member's login is
their email; `GET /api/members/me` returns their member record (and id).

## Lending rules

Configured in `application.yml` (or env vars), not stored in the database:

| Property | Default | Rule |
|---|---|---|
| `library.loan.max-active-loans` | 3 | A member can't hold more active loans than this |
| `library.loan.loan-duration-days` | 14 | Due date = borrow time + this many days |

A member with any overdue loan can't borrow. Rejected borrows return `409` with a `reason`:
`HAS_OVERDUE_LOAN`, `MAX_ACTIVE_LOANS_REACHED`, `ALREADY_BORROWED`, `NO_COPIES_AVAILABLE`.

## API

| Method | Path | Access |
|---|---|---|
| GET | /api/books?title=&author=&isbn= | any user (filters optional) |
| GET | /api/books/{id} | any user |
| POST, PUT, DELETE | /api/books/** | librarian |
| GET | /api/members/me | any user (own record) |
| GET, POST, PUT, DELETE | /api/members/** | librarian |
| POST | /api/loans | librarian, or a member for themselves |
| GET | /api/loans?status=ACTIVE\|OVERDUE\|RETURNED | librarian |
| GET | /api/loans/{id} | librarian, or the loan's member |
| POST | /api/loans/{id}/return | librarian, or the loan's member |
| GET | /api/members/{id}/loans | librarian, or that member |

Book search: `title` and `author` match partially and ignore case, `isbn` is exact; filters combine.
Full documentation in Swagger UI. Errors use RFC 7807 Problem Details.

```bash
curl -u rishabh@gmail.com:password123 -X POST localhost:8080/api/loans \
  -H "Content-Type: application/json" -d '{"memberId":1,"bookId":2}'
```

## Database

PostgreSQL with Flyway migrations in `src/main/resources/db/migration`. Demo data is in
`db/seed`; set `FLYWAY_LOCATIONS=classpath:db/migration` to start without it.

## Observability

- `/actuator/health` and `/actuator/info` (public)
- `/actuator/metrics` (librarian): `library.loans.borrowed`, `.returned`, `.rejected`, `.active`, `.overdue`
- Every request gets an `X-Request-Id`, included in log lines and the response header

## Tests

```bash
./mvnw test   # Docker must be running
```

Unit tests for the lending rules, integration tests against real PostgreSQL (Testcontainers)
covering the API, security and validation, and a concurrency test that fires parallel borrows
to check the loan limit and the last copy hold under contention.
