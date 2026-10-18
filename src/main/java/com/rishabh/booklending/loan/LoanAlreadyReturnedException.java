package com.rishabh.booklending.loan;

public class LoanAlreadyReturnedException extends RuntimeException {

    public LoanAlreadyReturnedException(Long loanId) {
        super("Loan " + loanId + " has already been returned");
    }
}