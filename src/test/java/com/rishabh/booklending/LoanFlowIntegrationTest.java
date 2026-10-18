package com.rishabh.booklending;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rishabh.booklending.book.Book;
import com.rishabh.booklending.book.BookRepository;
import com.rishabh.booklending.loan.Loan;
import com.rishabh.booklending.loan.LoanRepository;
import com.rishabh.booklending.member.Member;
import com.rishabh.booklending.member.MemberRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end loan flow through the real security config (HTTP Basic users from
 * application.yml), Flyway seed data and PostgreSQL via Testcontainers.
 * Each test runs in a transaction that is rolled back afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class LoanFlowIntegrationTest {

    // Logins from library.security.users in application.yml
    private static final RequestPostProcessor LIBRARIAN = httpBasic("librarian", "librarian123");
    private static final RequestPostProcessor RISHABH = httpBasic("rishabh@gmail.com", "password123");

    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired BookRepository books;
    @Autowired LoanRepository loans;

    // Members from V2__seed_data.sql
    private Member rishabh;
    private Member shivam;
    private Book singleCopy;

    @BeforeEach
    void setUp() {
        rishabh = members.findByEmail("rishabh@gmail.com").orElseThrow();
        shivam = members.findByEmail("shivamgarg@gmail.com").orElseThrow();
        singleCopy = books.save(new Book("Refactoring", "Martin Fowler", "9780134757599", 1));
    }

    private ResultActions borrow(RequestPostProcessor as, Member member, Book book) throws Exception {
        return mvc.perform(post("/api/loans").with(as)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"memberId": %d, "bookId": %d}
                         """.formatted(member.getId(), book.getId())));
    }

    private ResultActions returnLoan(RequestPostProcessor as, long loanId) throws Exception {
        return mvc.perform(post("/api/loans/{id}/return", loanId).with(as));
    }

    private void expectRejected(ResultActions result, String reason) throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Borrowing rule violated"))
                .andExpect(jsonPath("$.reason").value(reason));
    }

    // --- authentication & authorization ---

    @Test
    void requestsWithoutCredentialsGet401AsProblemDetails() throws Exception {
        mvc.perform(get("/api/books"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Basic")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void wrongPasswordGets401() throws Exception {
        mvc.perform(get("/api/books").with(httpBasic("rishabh@gmail.com", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void memberCannotAddBooks() throws Exception {
        mvc.perform(post("/api/books").with(RISHABH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"title":"X","author":"Y","isbn":"9780000000001","totalCopies":1}
                                 """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void memberCannotBorrowForSomeoneElse() throws Exception {
        borrow(RISHABH, shivam, singleCopy)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));

        assertThat(loans.existsByMemberId(shivam.getId())).isFalse();
    }

    @Test
    void memberCanSeeOwnLoansButNotOthers() throws Exception {
        mvc.perform(get("/api/members/{id}/loans", rishabh.getId()).with(RISHABH))
                .andExpect(status().isOk());
        mvc.perform(get("/api/members/{id}/loans", shivam.getId()).with(RISHABH))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberCannotReturnSomeoneElsesLoan() throws Exception {
        String body = borrow(LIBRARIAN, shivam, singleCopy)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long loanId = JsonPath.parse(body).read("$.id", Long.class);

        returnLoan(RISHABH, loanId).andExpect(status().isForbidden());
    }

    // --- borrow & return ---

    @Test
    void memberBorrowsAndReturnsOwnBook() throws Exception {
        String body = borrow(RISHABH, rishabh, singleCopy)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(rishabh.getId()))
                .andExpect(jsonPath("$.bookId").value(singleCopy.getId()))
                .andExpect(jsonPath("$.bookTitle").value("Refactoring"))
                .andExpect(jsonPath("$.returnedAt").value(nullValue()))
                .andReturn().getResponse().getContentAsString();

        long loanId = JsonPath.parse(body).read("$.id", Long.class);
        Instant borrowedAt = Instant.parse(JsonPath.parse(body).read("$.borrowedAt", String.class));
        Instant dueDate = Instant.parse(JsonPath.parse(body).read("$.dueDate", String.class));
        assertThat(Duration.between(borrowedAt, dueDate)).isEqualTo(Duration.ofDays(14));

        mvc.perform(get("/api/books/{id}", singleCopy.getId()).with(RISHABH))
                .andExpect(jsonPath("$.availableCopies").value(0));

        returnLoan(RISHABH, loanId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnedAt").isNotEmpty());

        mvc.perform(get("/api/books/{id}", singleCopy.getId()).with(RISHABH))
                .andExpect(jsonPath("$.availableCopies").value(1));

        returnLoan(LIBRARIAN, loanId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Loan " + loanId + " has already been returned"));
    }

    @Test
    void borrowReturnsLocationOfNewLoan() throws Exception {
        var response = borrow(RISHABH, rishabh, singleCopy)
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        long loanId = JsonPath.parse(response.getContentAsString()).read("$.id", Long.class);

        assertThat(response.getHeader("Location")).isEqualTo("/api/loans/" + loanId);
        mvc.perform(get(response.getHeader("Location")).with(RISHABH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(loanId))
                .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    void memberCannotReadSomeoneElsesLoan() throws Exception {
        String body = borrow(LIBRARIAN, shivam, singleCopy)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long loanId = JsonPath.parse(body).read("$.id", Long.class);

        mvc.perform(get("/api/loans/{id}", loanId).with(RISHABH))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/loans/{id}", loanId).with(LIBRARIAN))
                .andExpect(status().isOk());
    }

    // --- borrowing rules ---

    @Test
    void lastCopyCannotBeBorrowedByAnotherMember() throws Exception {
        borrow(RISHABH, rishabh, singleCopy).andExpect(status().isCreated());

        expectRejected(borrow(LIBRARIAN, shivam, singleCopy), "NO_COPIES_AVAILABLE");
    }

    @Test
    void memberCannotBorrowSameBookTwice() throws Exception {
        borrow(RISHABH, rishabh, singleCopy).andExpect(status().isCreated());

        expectRejected(borrow(RISHABH, rishabh, singleCopy), "ALREADY_BORROWED");
    }

    @Test
    void fourthActiveLoanIsRejected() throws Exception {
        for (int i = 1; i <= 3; i++) {
            Book book = books.save(new Book("Book " + i, "Author", "978000000000" + i, 1));
            borrow(RISHABH, rishabh, book).andExpect(status().isCreated());
        }

        expectRejected(borrow(RISHABH, rishabh, singleCopy), "MAX_ACTIVE_LOANS_REACHED");
        mvc.perform(get("/api/books/{id}", singleCopy.getId()).with(RISHABH))
                .andExpect(jsonPath("$.availableCopies").value(1));
    }

    @Test
    void memberWithOverdueLoanIsBlocked() throws Exception {
        overdueLoanOf(rishabh);

        expectRejected(borrow(RISHABH, rishabh, singleCopy), "HAS_OVERDUE_LOAN");
    }

    @Test
    void borrowingUnknownBookGives404() throws Exception {
        mvc.perform(post("/api/loans").with(RISHABH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"memberId": %d, "bookId": 999999}
                                 """.formatted(rishabh.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Book 999999 not found"));
    }

    // --- members ---

    private ResultActions updateMember(Member member, String name, String email) throws Exception {
        return mvc.perform(put("/api/members/{id}", member.getId()).with(LIBRARIAN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                         {"name": "%s", "email": "%s"}
                         """.formatted(name, email)));
    }

    @Test
    void librarianGetsMemberById() throws Exception {
        mvc.perform(get("/api/members/{id}", rishabh.getId()).with(LIBRARIAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rishabh.getId()))
                .andExpect(jsonPath("$.name").value("Rishabh Karnwal"))
                .andExpect(jsonPath("$.email").value("rishabh@gmail.com"));
    }

    @Test
    void memberCanBeRenamedWhileKeepingSameEmail() throws Exception {
        updateMember(rishabh, "Rishabh K", "rishabh@gmail.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rishabh K"))
                .andExpect(jsonPath("$.email").value("rishabh@gmail.com"));
    }

    @Test
    void memberCannotTakeAnotherMembersEmail() throws Exception {
        updateMember(rishabh, "Rishabh Karnwal", "shivamgarg@gmail.com")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email shivamgarg@gmail.com is already used by another member."));
    }

    // --- librarian loan views ---

    private Loan overdueLoanOf(Member member) {
        Book book = books.save(new Book("Overdue Book", "Author", "9780000000009", 1));
        Instant borrowedAt = Instant.now().minus(Duration.ofDays(30));
        return loans.save(new Loan(member, book, borrowedAt, borrowedAt.plus(Duration.ofDays(14))));
    }

    @Test
    void librarianSeesOnlyOverdueLoansWhenFiltering() throws Exception {
        Loan overdue = overdueLoanOf(shivam);
        borrow(RISHABH, rishabh, singleCopy).andExpect(status().isCreated());

        mvc.perform(get("/api/loans").param("status", "OVERDUE").with(LIBRARIAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(overdue.getId()))
                .andExpect(jsonPath("$.content[0].overdue").value(true));

        mvc.perform(get("/api/loans").param("status", "ACTIVE").with(LIBRARIAN))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void membersCannotListAllLoans() throws Exception {
        mvc.perform(get("/api/loans").with(RISHABH))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownLoanStatusIsA400() throws Exception {
        mvc.perform(get("/api/loans").param("status", "LOST").with(LIBRARIAN))
                .andExpect(status().isBadRequest());
    }

    // --- observability ---

    @Test
    void healthIsPublicButMetricsNeedALibrarian() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics").with(RISHABH))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics/library.loans.overdue").with(LIBRARIAN))
                .andExpect(status().isOk());
    }
}
