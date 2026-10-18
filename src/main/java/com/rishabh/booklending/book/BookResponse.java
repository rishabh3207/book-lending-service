package com.rishabh.booklending.book;

public record BookResponse(Long id, String title, String author, String isbn,
                           int totalCopies, int availableCopies) {

    static BookResponse from(Book book) {
        return new BookResponse(book.getId(), book.getTitle(), book.getAuthor(),
                book.getIsbn(), book.getTotalCopies(), book.getAvailableCopies());
    }
}