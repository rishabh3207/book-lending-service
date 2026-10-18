package com.rishabh.booklending.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rishabh.booklending.book.Book;
import com.rishabh.booklending.book.BookRepository;
import com.rishabh.booklending.common.NotFoundException;
import com.rishabh.booklending.config.LoanPolicyProperties;
import com.rishabh.booklending.loan.BorrowingRejectedException.Reason;
import com.rishabh.booklending.member.Member;
import com.rishabh.booklending.member.MemberRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");
    private static final long MEMBER_ID = 1L;
    private static final long BOOK_ID = 3L;
    private static final long LOAN_ID = 5L;

    // Same values as library.loan.* in application.yml
    private static final int MAX_ACTIVE_LOANS = 3;
    private static final int LOAN_DURATION_DAYS = 14;

    @Mock MemberRepository members;
    @Mock BookRepository books;
    @Mock LoanRepository loans;

    private SimpleMeterRegistry meters;
    private LoanService service;
    private Member rishabh;
    private Book ddia;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        service = new LoanService(members, books, loans,
                new LoanPolicyProperties(MAX_ACTIVE_LOANS, LOAN_DURATION_DAYS),
                Clock.fixed(NOW, ZoneOffset.UTC), meters);
        rishabh = new Member("Rishabh Karnwal", "rishabh@gmail.com");
        // Single-copy book from the seed data, so one loan empties the shelf
        ddia = new Book("Designing Data Intensive Applications", "Martin Kleppmann", "9781449373320", 1);
    }

    private void memberAndBookExist() {
        when(members.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(rishabh));
        when(books.findById(BOOK_ID)).thenReturn(Optional.of(ddia));
    }

    private Loan activeLoanOfDdia() {
        ddia.checkOutCopy();
        return new Loan(rishabh, ddia, NOW.minus(Duration.ofDays(3)), NOW.plus(Duration.ofDays(11)));
    }

    private void assertRejected(ThrowingCallable borrow, Reason reason) {
        assertThatThrownBy(borrow)
                .isInstanceOfSatisfying(BorrowingRejectedException.class,
                        e -> assertThat(e.getReason()).isEqualTo(reason));
        verify(loans, never()).save(any());
        assertThat(rejectedCount(reason)).isEqualTo(1.0);
        assertThat(meters.counter("library.loans.borrowed").count()).isZero();
    }

    private double rejectedCount(Reason reason) {
        return meters.counter("library.loans.rejected", "reason", reason.name()).count();
    }

    // --- borrow ---

    @Test
    void borrowCreatesLoanDueAfterPolicyDurationAndTakesTheCopy() {
        memberAndBookExist();
        when(loans.save(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));

        Loan loan = service.borrow(MEMBER_ID, BOOK_ID);

        assertThat(loan.getMember()).isSameAs(rishabh);
        assertThat(loan.getBook()).isSameAs(ddia);
        assertThat(loan.getBorrowedAt()).isEqualTo(NOW);
        assertThat(loan.getDueDate()).isEqualTo(NOW.plus(Duration.ofDays(LOAN_DURATION_DAYS)));
        assertThat(loan.getReturnedAt()).isNull();
        assertThat(ddia.getAvailableCopies()).isZero();
        assertThat(meters.counter("library.loans.borrowed").count()).isEqualTo(1.0);
    }

    @Test
    void borrowFailsWhenMemberDoesNotExist() {
        when(members.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.borrow(MEMBER_ID, BOOK_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Member 1 not found");
        verify(books, never()).findById(anyLong());
    }

    @Test
    void borrowFailsWhenBookDoesNotExist() {
        when(members.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(rishabh));
        when(books.findById(BOOK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.borrow(MEMBER_ID, BOOK_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Book 3 not found");
        verify(loans, never()).save(any());
    }

    @Test
    void memberWithOverdueLoanIsRejectedBeforeAnyOtherRule() {
        memberAndBookExist();
        when(loans.existsByMemberIdAndReturnedAtIsNullAndDueDateBefore(MEMBER_ID, NOW)).thenReturn(true);

        assertRejected(() -> service.borrow(MEMBER_ID, BOOK_ID), Reason.HAS_OVERDUE_LOAN);

        verify(loans, never()).countByMemberIdAndReturnedAtIsNull(anyLong());
        assertThat(ddia.getAvailableCopies()).isEqualTo(1);
    }

    @Test
    void memberOneBelowLoanLimitCanStillBorrow() {
        memberAndBookExist();
        when(loans.countByMemberIdAndReturnedAtIsNull(MEMBER_ID)).thenReturn((long) MAX_ACTIVE_LOANS - 1);
        when(loans.save(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));

        service.borrow(MEMBER_ID, BOOK_ID);

        assertThat(meters.counter("library.loans.borrowed").count()).isEqualTo(1.0);
    }

    @Test
    void memberAtLoanLimitIsRejected() {
        memberAndBookExist();
        when(loans.countByMemberIdAndReturnedAtIsNull(MEMBER_ID)).thenReturn((long) MAX_ACTIVE_LOANS);

        assertRejected(() -> service.borrow(MEMBER_ID, BOOK_ID), Reason.MAX_ACTIVE_LOANS_REACHED);
        assertThatThrownBy(() -> service.borrow(MEMBER_ID, BOOK_ID))
                .hasMessage("You already have 3 active loans (limit 3)");
    }

    @Test
    void memberCannotBorrowSameBookTwice() {
        memberAndBookExist();
        when(loans.existsByMemberIdAndBookIdAndReturnedAtIsNull(MEMBER_ID, BOOK_ID)).thenReturn(true);

        assertRejected(() -> service.borrow(MEMBER_ID, BOOK_ID), Reason.ALREADY_BORROWED);

        assertThat(ddia.getAvailableCopies()).isEqualTo(1);
    }

    @Test
    void cannotBorrowWhenNoCopiesLeft() {
        ddia.checkOutCopy();
        memberAndBookExist();

        assertRejected(() -> service.borrow(MEMBER_ID, BOOK_ID), Reason.NO_COPIES_AVAILABLE);

        assertThat(ddia.getAvailableCopies()).isZero();
    }

    // --- return ---

    @Test
    void returningPutsCopyBackAndRecordsReturnTime() {
        Loan loan = activeLoanOfDdia();
        when(loans.findById(LOAN_ID)).thenReturn(Optional.of(loan));

        Loan returned = service.returnLoan(LOAN_ID);

        assertThat(returned.getReturnedAt()).isEqualTo(NOW);
        assertThat(ddia.getAvailableCopies()).isEqualTo(1);
        assertThat(meters.counter("library.loans.returned").count()).isEqualTo(1.0);
    }

    @Test
    void returningTwiceIsRejectedAndDoesNotAddACopy() {
        Loan loan = activeLoanOfDdia();
        loan.markReturned(NOW.minus(Duration.ofDays(1)));
        ddia.returnCopy();
        when(loans.findById(LOAN_ID)).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> service.returnLoan(LOAN_ID))
                .isInstanceOf(LoanAlreadyReturnedException.class)
                .hasMessageEndingWith("has already been returned");

        assertThat(loan.getReturnedAt()).isEqualTo(NOW.minus(Duration.ofDays(1)));
        assertThat(ddia.getAvailableCopies()).isEqualTo(1);
        assertThat(meters.counter("library.loans.returned").count()).isZero();
    }

    @Test
    void returningUnknownLoanFails() {
        when(loans.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.returnLoan(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Loan 99 not found");
    }
}
