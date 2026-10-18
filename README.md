# Book Lending Service

[![CI](https://github.com/rishabh3207/book-lending-service/actions/workflows/ci.yml/badge.svg)](https://github.com/rishabh3207/book-lending-service/actions/workflows/ci.yml)

A Spring Boot microservice for managing a library catalog and members, and for borrowing
and returning books under configurable lending rules.

## Evaluate in 5 minutes

```bash
docker compose up --build          # app on :8080, Postgres on :5432, demo data loaded
```

Open http://localhost:8080/swagger-ui.html, click **Authorize**, and log in as `librarian` / `librarian123`
or `rishabh@gmail.com` / `password123`. Or from a terminal:

```bash
# A member borrows a book: 201 with a Location header and a due date 14 days out
curl -i -u rishabh@gmail.com:password123 -X POST localhost:8080/api/loans \
  -H "Content-Type: application/json" -d '{"memberId":1,"bookId":2}'

# Borrow books 1 and 3, then try book 1 again: the last call is 409 "MAX_ACTIVE_LOANS_REACHED"
for b in 1 3 1; do curl -s -u rishabh@gmail.com:password123 -X POST localhost:8080/api/loans \
  -H "Content-Type: application/json" -d "{\"memberId\":1,\"bookId\":$b}"; echo; done

# The librarian's view of who has what (also: status=OVERDUE or RETURNED)
curl -s -u librarian:librarian123 "localhost:8080/api/loans?status=ACTIVE"
```

The last call repeats book 1, yet the answer is the limit, not `ALREADY_BORROWED`: member-level rules
are checked first (see "Order of checks when borrowing" below).

## Tech stack
Java 25, Spring Boot 4, Spring Data JPA, PostgreSQL 16, Flyway, Spring Security (HTTP Basic),
Actuator + Micrometer, springdoc-openapi, JUnit 5, Mockito, Testcontainers,
Docker Compose, GitHub Actions.

## Running

Requirements: Docker.

```bash
docker compose up --build
```

- API: http://localhost:8080/api
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

Run locally without Docker for the app (Postgres still via Docker):

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

Run tests (Docker must be running for Testcontainers):

```bash
./mvnw test
```

If you ran an earlier version of this project, its Postgres volume may hold an older seed
migration and Flyway will refuse to start. Reset it with `docker compose down -v`.

## Users

| Username | Password | Role |
|---|---|---|
| librarian | librarian123 | LIBRARIAN |
| rishabh@gmail.com | password123 | MEMBER (member id 1) |
| shivamgarg@gmail.com | password123 | MEMBER (member id 2) |

Passwords are stored BCrypt-hashed in `application.yml`.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `library.loan.max-active-loans` | 3 | Max active loans per member |
| `library.loan.loan-duration-days` | 14 | Loan length used to compute the due date |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | local defaults | Database connection |
| `FLYWAY_LOCATIONS` | `classpath:db/migration,classpath:db/seed` | Set to `classpath:db/migration` in production to skip demo data |
| `spring.data.web.pageable.max-page-size` | 100 | Upper bound on `size` for every paginated endpoint |

## API overview

| Method | Path | Access |
|---|---|---|
| GET | /api/books | any user |
| POST, PUT, DELETE | /api/books/** | librarian |
| * | /api/members/** | librarian |
| POST | /api/loans | librarian, or member for themselves |
| GET | /api/loans?status=ACTIVE\|OVERDUE\|RETURNED | librarian |
| GET | /api/loans/{id} | librarian, or the loan's member |
| POST | /api/loans/{id}/return | librarian, or the loan's member |
| GET | /api/members/{id}/loans | librarian, or that member |

Every loan in a response carries an `overdue` flag, so clients don't need to compare dates themselves.

Every error, including 401 and 403 from the security layer, is an RFC 7807 Problem Details body.
Borrowing rule violations return 409 with a `reason` field: `HAS_OVERDUE_LOAN`,
`MAX_ACTIVE_LOANS_REACHED`, `ALREADY_BORROWED`, `NO_COPIES_AVAILABLE`.

### Example

```bash
curl -u rishabh@gmail.com:password123 -X POST http://localhost:8080/api/loans \
  -H "Content-Type: application/json" -d '{"memberId":1,"bookId":2}'
```

## Data model

There are three tables. A `book` stores the title, author, a unique ISBN, and two counters:
`total_copies` and `available_copies`. A `member` has a name and a unique email. A `loan` links
one member to one book and records `borrowed_at`, `due_date` and `returned_at`; a loan with
`returned_at` null is active. A member can have many loans and a book can have many loans, so
`loan` is effectively the join table between them, but with its own lifecycle. I don't keep
loan history anywhere else, so loans are never deleted.

## Design decisions

**Package by feature.** The code is split into `book`, `member` and `loan` packages, each holding
its own entity, repository, service, controller and DTOs, with `common` for errors and `config`
for security, time and properties. When I work on lending I only open the `loan` package, and it
keeps package-private things (like the `from()` mappers on the DTOs) from leaking everywhere.

**Flyway with `ddl-auto: validate`.** The schema is owned by SQL migrations, and Hibernate only
checks that the entities match it. I wanted the schema to be reviewable and versioned rather than
generated. The database also enforces the important rules on its own: unique ISBN and email,
`available_copies` between 0 and `total_copies`, due date after borrow date, and a partial unique
index so a member can't hold two active loans of the same book. The service checks these first
for nice error messages, but the constraints are the last line of defence if a bug or a race gets
past the code. Demo data lives in a separate `db/seed` location that production leaves out via
`FLYWAY_LOCATIONS`, so the seed rows never reach a real database.

**Lending rules in config.** The max number of active loans and the loan duration live under
`library.loan` and bind to a `@ConfigurationProperties` record with `@Validated` and `@Min(1)`.
They can change per environment without a code change, and a bad value like 0 stops the app at
startup instead of silently blocking every borrow.

**Order of checks when borrowing.** The service first checks that the member and book exist, then
overdue loans, then the active-loan limit, then whether the member already has this book, and only
then available copies. Overdue comes first because it is the most important thing for the member
to fix, and if they are blocked anyway there's no point telling them about anything else. The
member-level checks come before the book check because they depend only on the member's own
state, while copy availability depends on everyone else and is the one most likely to change
between two attempts.

**Concurrency.** `Book` has a `@Version` column, so if two people take the last copy at the same
time one of the updates fails with an optimistic-lock error, which I return as 409 "Concurrent
update" so the client can retry. The DB check constraint on `available_copies` backs this up, and
the partial unique index covers two simultaneous borrows of the same book by the same member. The
member limit needed something different: two parallel borrows of *different* books could both
count 2 active loans and both succeed, and no single row or constraint catches that. So `borrow`
loads the member with `findByIdForUpdate` (`SELECT ... FOR UPDATE`), which makes one member's
borrows run one after another; the second one waits and then counts the first loan. I used a
pessimistic lock here instead of another version column because the conflict is on a count across
rows, and the lock is only held per member for one short transaction.

`LoanConcurrencyIntegrationTest` checks both mechanisms with 8 threads released at the same
moment. I also checked that the test fails for the right reason: with `findByIdForUpdate` swapped
for a plain `findById`, all 8 parallel borrows succeed against a limit of 3.

**409 for rule violations.** I used 409 Conflict rather than 422 for broken borrowing rules. The
request itself is well-formed and valid; it's refused because of the current state of the
member's loans or the book's stock, which is what 409 describes. 400 is kept for actual validation
errors. Every rule violation also has a machine-readable `reason` so clients don't need to parse
the message.

**Member login and 403 vs 404.** A member's login username is their email, and `AccessGuard`
checks it against the member record before allowing borrow, return or loan history. When a member
asks about someone else's data, or a loan id that doesn't exist, they get 403 in both cases. That
way a member can't probe ids to find out which loans or members exist. Librarians still get a
proper 404.

**ISBN can't change, books with history can't be deleted.** The ISBN identifies the edition, so
changing it would really be a different book; to fix a wrong ISBN you add a new book. Deleting a
book or member that has any loans (even returned ones) is blocked with 409, because it would
either break the foreign keys or wipe out lending history. Reducing `total_copies` below the
number currently on loan is also a 409.

**Exception strategy.** Only the service's own exceptions (`NotFoundException`, `ConflictException`,
`BorrowingRejectedException`, `LoanAlreadyReturnedException`) map to 4xx. The `IllegalStateException`
guards inside `Book` protect invariants the service has already checked, so if one ever fires it's a
bug and correctly becomes a 500. Mapping JDK exceptions to 409 globally would also have turned
exceptions thrown inside Spring or Hibernate into "conflicts" and leaked their messages to clients.
Request DTOs carry the same size limits as the columns, so bad input is a 400 with a per-field
`errors` map and never reaches the database as a constraint error.

**One email form.** Emails are login usernames, so `Member` lower-cases and trims them in one place,
and both ownership checks in `AccessGuard` compare that form. A unique index on `lower(email)` (V3)
enforces it in the database too. Before that, `Rishabh@gmail.com` could be registered next to
`rishabh@gmail.com`, and a member with a mixed-case email could borrow but not return.

**Injected `Clock`.** `LoanService` takes a `Clock` bean instead of calling `Instant.now()`. In
production it's `Clock.systemUTC()`, and in the unit tests I pass a fixed clock, so due dates and
the overdue check can be asserted exactly instead of with "roughly now" tolerances.

**`created_at` vs `borrowed_at`.** `borrowed_at`, `due_date` and `returned_at` are business times
set by the service from the injected clock, and they are what the lending rules use.
`created_at`/`updated_at` are audit columns filled in by Hibernate and only say when the row
changed. Keeping them separate means I could backfill or import old loans with correct borrow
dates without lying about when the row was written.

## Observability
- Health: /actuator/health (public; details for librarian)
- Info: /actuator/info shows the build version and time
- Metrics (librarian): /actuator/metrics
  - counters `library.loans.borrowed`, `library.loans.returned`, `library.loans.rejected` (tagged by
    reason) show throughput
  - gauges `library.loans.active` and `library.loans.overdue` show the current state of the library
- Every request gets an `X-Request-Id`, included in all log lines and the response header

## Testing

54 tests, run on every push by GitHub Actions.

| Suite | Kind | What it proves |
|---|---|---|
| `LoanServiceTest` | unit (Mockito, fixed `Clock`) | each borrowing rule, the limit boundary, rule order, return flow, metrics; runs in under a second |
| `BookTest` | unit | copy arithmetic when total copies change; counters never leave their range |
| `LoanFlowIntegrationTest` | MockMvc + Postgres | the real HTTP Basic users, 401/403 bodies, borrow/return, every rejection reason, loan lookup, librarian views, actuator access |
| `BookAndMemberIntegrationTest` | MockMvc + Postgres | validation (400 with field errors), bad sort, page cap, ISBN and copy rules, delete protection, email normalisation |
| `LoanConcurrencyIntegrationTest` | real commits, 8 threads | the member limit and the last copy hold under parallel borrows |

The MockMvc suites run each test in a rolled-back transaction. The concurrency test deliberately
doesn't: a test transaction would put every call on one connection and roll back before commit, so
neither the row lock nor the `@Version` check would ever run. It cleans up its own rows instead.

I used Testcontainers instead of H2 because the schema relies on PostgreSQL features: partial
unique indexes, `TIMESTAMPTZ`, identity columns and check constraints. With H2 the Flyway
migrations would either not run or behave differently, and the tests would pass against a
database I don't actually deploy on.

## Known limitations and next steps

- **Users live in config.** The in-memory users in `application.yml` are fine for a demo, but a member
  created through the API can't log in. In production I'd use an identity provider with OAuth2/JWT and
  link the token subject to the member.
- **Missing features.** No overdue reminders, renewals, reservations or waitlist.
- **Gauges query on scrape.** `library.loans.active`/`overdue` run a count on each scrape. That's cheap
  with the partial indexes at this scale; at much larger scale I'd cache them for a short interval.
- **Ops extras.** No JSON logging yet. The Docker image is multi-stage and runs as a non-root user, but
  copies the fat jar as one layer; extracting the jar's layers would make rebuilds smaller.
