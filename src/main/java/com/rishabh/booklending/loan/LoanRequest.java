package com.rishabh.booklending.loan;

import jakarta.validation.constraints.NotNull;

public record LoanRequest(
    @NotNull Long memberId, @NotNull Long bookId
) {
} 
