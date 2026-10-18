package com.rishabh.booklending.loan;

import java.time.Instant;

public record LoanResponse(Long id, Long memberId, Long bookId, String bookTitle,
                           Instant borrowedAt, Instant dueDate, Instant returnedAt, boolean overdue) {

    static LoanResponse from(Loan loan, Instant now) {
        return new LoanResponse(loan.getId(), loan.getMember().getId(), loan.getBook().getId(),
                loan.getBook().getTitle(), loan.getBorrowedAt(), loan.getDueDate(), loan.getReturnedAt(),
                loan.isOverdue(now));
    }
}
