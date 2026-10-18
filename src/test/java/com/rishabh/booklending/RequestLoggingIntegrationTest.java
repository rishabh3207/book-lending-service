package com.rishabh.booklending;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rishabh.booklending.book.Book;
import com.rishabh.booklending.book.BookRepository;
import com.rishabh.booklending.loan.Loan;
import com.rishabh.booklending.loan.LoanRepository;
import com.rishabh.booklending.member.Member;
import com.rishabh.booklending.member.MemberRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Checks the real log output: one access line per request, tagged with request id and user. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired BookRepository books;
    @Autowired LoanRepository loans;

    private static String lineContaining(CapturedOutput output, String... parts) {
        return output.getAll().lines()
                .filter(line -> java.util.Arrays.stream(parts).allMatch(line::contains))
                .findFirst()
                .orElse(null);
    }

    @Test
    void accessLineHasMethodPathStatusDurationRequestIdAndUser(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/members/me").header("X-Request-Id", "log-test-1")
                        .with(httpBasic("rishabh@gmail.com", "password123")))
                .andExpect(status().isOk());

        assertThat(lineContaining(output, "[log-test-1 rishabh@gmail.com]", "GET /api/members/me -> 200 ("))
                .isNotNull()
                .containsPattern("\\(\\d+ ms\\)");
    }

    @Test
    @Transactional
    void businessLogLinesCarryTheUserWhoActed(CapturedOutput output) throws Exception {
        mvc.perform(post("/api/loans").header("X-Request-Id", "log-test-2")
                        .with(httpBasic("librarian", "librarian123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\": 2, \"bookId\": 1}"))
                .andExpect(status().isCreated());

        // The librarian borrowed on Shivam's behalf: the log says who did it, not just for whom
        assertThat(lineContaining(output, "[log-test-2 librarian]", "created: member=2 book=1")).isNotNull();
        assertThat(lineContaining(output, "[log-test-2 librarian]", "POST /api/loans -> 201")).isNotNull();
    }

    @Test
    @Transactional
    void lateReturnIsLoggedWithDaysLate(CapturedOutput output) throws Exception {
        Member rishabh = members.findByEmail("rishabh@gmail.com").orElseThrow();
        Book book = books.save(new Book("Late Book", "Author", "9780000000062", 1));
        book.checkOutCopy();
        Instant borrowedAt = Instant.now().minus(Duration.ofDays(20));
        Loan loan = loans.save(new Loan(rishabh, book, borrowedAt, borrowedAt.plus(Duration.ofDays(14))));

        mvc.perform(post("/api/loans/{id}/return", loan.getId()).header("X-Request-Id", "log-test-6")
                        .with(httpBasic("rishabh@gmail.com", "password123")))
                .andExpect(status().isOk());

        assertThat(lineContaining(output, "[log-test-6 rishabh@gmail.com]", "Loan " + loan.getId() + " returned late",
                "daysLate=6")).isNotNull();
    }

    @Test
    @Transactional
    void rejectedReturnIsLogged(CapturedOutput output) throws Exception {
        Member rishabh = members.findByEmail("rishabh@gmail.com").orElseThrow();
        Book book = books.save(new Book("Returned Book", "Author", "9780000000079", 1));
        Loan loan = new Loan(rishabh, book, Instant.now().minus(Duration.ofDays(2)), Instant.now().plus(Duration.ofDays(12)));
        loan.markReturned(Instant.now());
        loan = loans.save(loan);

        mvc.perform(post("/api/loans/{id}/return", loan.getId()).header("X-Request-Id", "log-test-7")
                        .with(httpBasic("librarian", "librarian123")))
                .andExpect(status().isConflict());

        assertThat(lineContaining(output, "[log-test-7 librarian]",
                "Return rejected: Loan " + loan.getId() + " has already been returned")).isNotNull();
    }

    @Test
    void failedLoginIsLoggedWithoutAUser(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/books").header("X-Request-Id", "log-test-3")
                        .with(httpBasic("rishabh@gmail.com", "wrong")))
                .andExpect(status().isUnauthorized());

        assertThat(lineContaining(output, "[log-test-3 ]", "GET /api/books -> 401")).isNotNull();
    }

    @Test
    void loggingContextIsClearedAfterTheRequest() throws Exception {
        mvc.perform(get("/api/books").with(httpBasic("rishabh@gmail.com", "password123")))
                .andExpect(status().isOk());

        // MockMvc runs on this thread; a leftover value would leak into the next request on a pooled thread
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("user")).isNull();
    }

    @Test
    void swaggerAssetsAreNotAccessLogged(CapturedOutput output) throws Exception {
        mvc.perform(get("/v3/api-docs").header("X-Request-Id", "log-test-5")).andExpect(status().isOk());

        assertThat(lineContaining(output, "GET /v3/api-docs ->")).isNull();
    }
}
