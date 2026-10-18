package com.rishabh.booklending;
import static org.assertj.core.api.Assertions.assertThat;

import com.rishabh.booklending.book.Book;
import com.rishabh.booklending.book.BookRepository;
import com.rishabh.booklending.config.LoanPolicyProperties;
import com.rishabh.booklending.loan.BorrowingRejectedException;
import com.rishabh.booklending.loan.BorrowingRejectedException.Reason;
import com.rishabh.booklending.loan.LoanRepository;
import com.rishabh.booklending.loan.LoanService;
import com.rishabh.booklending.member.Member;
import com.rishabh.booklending.member.MemberRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest 
@Import(TestcontainersConfiguration.class)
class LoanConcurrencyIntegrationTest {
    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> bookIds = new ArrayList<>();

    private static final int THREADS = 8;

    @Autowired LoanService loanService;
    @Autowired LoanPolicyProperties policy;
    @Autowired MemberRepository members;
    @Autowired BookRepository books;
    @Autowired LoanRepository loans;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        memberIds.forEach(id -> jdbc.update("DELETE FROM loan WHERE member_id = ?", id));
        bookIds.forEach(id -> jdbc.update("DELETE FROM book WHERE id = ?", id));
        memberIds.forEach(id -> jdbc.update("DELETE FROM member WHERE id = ?", id));
    }
    
    @Test
    void parallelBorrowsOfDifferentBooksNeverExceedTheMemberLimit() throws Exception {
        Member member = addNewMember(0);
        List<Book> shelf = IntStream.range(0, THREADS).mapToObj(i -> addNewBook(i, 1)).toList();

        List<Throwable> failures = race(i -> loanService.borrow(member.getId(), shelf.get(i).getId()));
        
        assertThat(THREADS - failures.size()).isEqualTo(policy.maxActiveLoans());
        assertThat(failures).allSatisfy(e -> assertThat(e)
                .isInstanceOfSatisfying(BorrowingRejectedException.class,
                        r -> assertThat(r.getReason()).isEqualTo(Reason.MAX_ACTIVE_LOANS_REACHED)));
        assertThat(loans.countByMemberIdAndReturnedAtIsNull(member.getId())).isEqualTo(policy.maxActiveLoans());
    }

    @Test
    void parallelBorrowsOfTheLastCopyLendItExactlyOnce() throws Exception {
        Book lastCopy = addNewBook(0, 1);
        List<Member> crowd = IntStream.range(0, THREADS).mapToObj(this::addNewMember).toList();

        List<Throwable> failures = race(i -> loanService.borrow(crowd.get(i).getId(), lastCopy.getId()));

        assertThat(THREADS - failures.size()).isEqualTo(1);
        assertThat(failures).allSatisfy(e -> assertThat(
                e instanceof OptimisticLockingFailureException
                        || (e instanceof BorrowingRejectedException r && r.getReason() == Reason.NO_COPIES_AVAILABLE))
                .as("unexpected failure: %s", e)
                .isTrue());
        assertThat(books.findById(lastCopy.getId()).orElseThrow().getAvailableCopies()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM loan WHERE book_id = ?", Long.class, lastCopy.getId()))
                .isEqualTo(1L);
    }

    private List<Throwable> race(IntConsumer attempt) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < THREADS; i++) {
                int n = i;
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        attempt.accept(n);
                        return null;
                    } catch (RuntimeException e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> result : results) {
                Throwable failure = result.get(30, TimeUnit.SECONDS);
                if (failure != null) {
                    failures.add(failure);
                }
            }
            return failures;
        }
    }

    private Member addNewMember(int n) {
        Member member = members.save(new Member("Test Member" + n, "TestMember" + n + "@example.test"));
        memberIds.add(member.getId());
        return member;
    }

    private Book addNewBook(int n, int copies) {
        Book book = books.save(new Book("Test Book Title" + n, "Test Book Author" + n,"9869912000%03d".formatted(n), copies));
        bookIds.add(book.getId());
        return book;
    }
}