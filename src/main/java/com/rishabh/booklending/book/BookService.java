package com.rishabh.booklending.book;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rishabh.booklending.common.ConflictException;
import com.rishabh.booklending.common.NotFoundException;
import com.rishabh.booklending.loan.LoanRepository;

@Service 
public class BookService {
    private final BookRepository books;
    private final LoanRepository loans;

    public BookService(BookRepository books, LoanRepository loans) {
        this.books = books;
        this.loans = loans;
    }

    @Transactional 
    public Book create(BookRequest bookRequest) {
        if(books.existsByIsbn(bookRequest.isbn())) {
            throw new ConflictException("Cannot create book: ISBN " + bookRequest.isbn() + " is already in use.");
        }
        return books.save(new Book(bookRequest.title(), bookRequest.author(), bookRequest.isbn(), bookRequest.totalCopies()));
    }

    @Transactional(readOnly = true)
    public Book get(Long id) {
        return books.findById(id).orElseThrow(() -> new NotFoundException("Book", id));
    }

    @Transactional(readOnly = true)
    public Page<Book> list(String title, String author, String isbn, Pageable pageable) {
        return books.findAll(BookSpecifications.matching(title, author, isbn), pageable);
    }

    @Transactional
    public Book update(Long id, BookRequest bookRequest) {
        Book book = get(id);
        if(!book.getIsbn().equals(bookRequest.isbn())) {
            throw new ConflictException("Cannot update ISBN.");
        }
        book.updateDetails(bookRequest.title(), bookRequest.author(), bookRequest.totalCopies());
        return book;
    }

    @Transactional
    public void delete(Long id){
        Book book = get(id);
        if(loans.existsByBookId(id)) {
            throw new ConflictException("Unable to delete the book because loan records exist.");
        }
        books.delete(book);
    }

}
