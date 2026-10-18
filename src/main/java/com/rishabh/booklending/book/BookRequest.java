package com.rishabh.booklending.book;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// Sizes mirror the columns in V1__create_schema.sql
public record BookRequest(
    @NotBlank @Size(max = 255) String title,
    @NotBlank @Size(max = 255) String author,
    @NotBlank @Pattern(regexp = "\\d{10}|\\d{13}", message = "ISBN must be 10 or 13 digits") String isbn,
    @NotNull @PositiveOrZero Integer totalCopies) {
}
