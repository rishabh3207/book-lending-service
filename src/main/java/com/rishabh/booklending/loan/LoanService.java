package com.rishabh.booklending.loan;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import com.rishabh.booklending.book.Book;
import com.rishabh.booklending.book.BookRepository;
import com.rishabh.booklending.common.NotFoundException;
import com.rishabh.booklending.config.LoanPolicyProperties;
import com.rishabh.booklending.loan.BorrowingRejectedException.Reason;
import com.rishabh.booklending.member.Member;
import com.rishabh.booklending.member.MemberRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class LoanService {
    
    private final MemberRepository members;
    private final BookRepository books;
    private final LoanRepository loans;
    private final LoanPolicyProperties policy;
    private final Clock clock;
    private static final Logger log = LoggerFactory.getLogger(LoanService.class);

    private final MeterRegistry meters;
    private final Counter borrowedCounter;
    private final Counter returnedCounter;

    public LoanService(MemberRepository members, BookRepository books, LoanRepository loans, LoanPolicyProperties policy, Clock clock, MeterRegistry meters){
        this.members = members;
        this.books = books;
        this.loans = loans;
        this.policy = policy;
        this.clock = clock;
        this.meters = meters;
        this.borrowedCounter = meters.counter("library.loans.borrowed");
        this.returnedCounter = meters.counter("library.loans.returned");
    }

    private BorrowingRejectedException reject(Reason reason, String message) {
        meters.counter("library.loans.rejected", "reason", reason.name()).increment();
        return new BorrowingRejectedException(reason, message);
    }

    @Transactional 
    public Loan borrow(Long memberId, Long bookId) {
        Member member =  members.findByIdForUpdate(memberId).orElseThrow(() ->  new NotFoundException("Member", memberId));
        Book book = books.findById(bookId).orElseThrow(() -> new NotFoundException("Book", bookId));
        Instant now = clock.instant();

        if(loans.existsByMemberIdAndReturnedAtIsNullAndDueDateBefore(memberId, now)) {
            throw reject(Reason.HAS_OVERDUE_LOAN, "You already have overdue loan, please return it first to borrow another.");
        }

        long activeLoansCount = loans.countByMemberIdAndReturnedAtIsNull(memberId);
        if(activeLoansCount >= policy.maxActiveLoans()) {
            throw reject(Reason.MAX_ACTIVE_LOANS_REACHED, "You already have " + activeLoansCount + " active loans (limit " + policy.maxActiveLoans() + ")");
        }
        
        if(loans.existsByMemberIdAndBookIdAndReturnedAtIsNull(memberId, bookId)) {
            throw reject(Reason.ALREADY_BORROWED, "You already have active loan for this book.");
        }

        if(book.getAvailableCopies() == 0){ 
            throw reject(Reason.NO_COPIES_AVAILABLE, "No copies available for this book.");
        }

        book.checkOutCopy();

        Instant dueDate = now.plus(Duration.ofDays(policy.loanDurationDays()));
        Loan loan = loans.save(new Loan(member, book, now, dueDate));
        borrowedCounter.increment();
        log.info("Loan {} created: member={} book={} due={}", loan.getId(), memberId, bookId, dueDate);
        return loan;

    }

    @Transactional 
    public Loan returnLoan(Long loanId) {
        Loan loan = loans.findById(loanId).orElseThrow(() -> new NotFoundException("Loan", loanId));
        if(loan.getReturnedAt() != null) {
            throw new LoanAlreadyReturnedException(loanId);
        }
        loan.markReturned(clock.instant());
        loan.getBook().returnCopy();
        returnedCounter.increment();
        log.info("Loan {} returned: member={} book={}", loanId, loan.getMember().getId(), loan.getBook().getId());
        return loan;
    }

    @Transactional(readOnly = true)
    public Page<Loan> loansOfMember(Long memberId, Pageable pageable) {
        if (!members.existsById(memberId)) {
            throw new NotFoundException("Member", memberId);
        }
        return loans.findByMemberId(memberId, pageable);
    }


}
