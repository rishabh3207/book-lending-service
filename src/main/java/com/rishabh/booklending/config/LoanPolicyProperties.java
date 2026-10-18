package com.rishabh.booklending.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "library.loan")
public record LoanPolicyProperties(
        @Min(1) int maxActiveLoans,
        @Min(1) int loanDurationDays) {
}