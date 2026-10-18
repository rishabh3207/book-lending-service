package com.rishabh.booklending.loan;

public class BorrowingRejectedException extends RuntimeException {

    public enum Reason {
        HAS_OVERDUE_LOAN,
        MAX_ACTIVE_LOANS_REACHED,
        ALREADY_BORROWED,
        NO_COPIES_AVAILABLE
    }

    private final Reason reason;

    public BorrowingRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}