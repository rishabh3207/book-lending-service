package com.rishabh.booklending.book;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BookRequest (
    @NotBlank String title,
    @NotBlank String author,
    @NotBlank @Pattern(regexp = "\\d{10}|\\d{13}", message = "ISBN should be 10 io 13 digits") String isbn,
    @Min(0) int totalCopies) {

    }
