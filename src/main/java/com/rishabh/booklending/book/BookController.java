package com.rishabh.booklending.book;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;

import java.net.URI;

import org.springframework.data.domain.Pageable;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;

@Tag(name = "Books", description = "Browse the catalogue; librarians add, update and remove books")
@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @Operation(summary = "Get a book by id")
    @ApiResponse(responseCode = "200", description = "Book found")
    @ApiResponse(responseCode = "404", description = "Book not found")
    @GetMapping("/{id}")
    public BookResponse get(@PathVariable Long id){
        return BookResponse.from(bookService.get(id));
    }

    @Operation(summary = "List books", description = "Paginated; available to any logged-in user.")
    @GetMapping
    public PagedModel<BookResponse> list(@ParameterObject Pageable pageable) {
        return new PagedModel<>(bookService.list(pageable).map(BookResponse::from));
    }

    @Operation(summary = "Add a book",
            description = "Librarians only. All copies start as available. ISBN must be 10 or 13 digits and unique.")
    @ApiResponse(responseCode = "201", description = "Book created")
    @ApiResponse(responseCode = "400", description = "Validation failed; see 'errors' in the body")
    @ApiResponse(responseCode = "403", description = "Only librarians can add books")
    @ApiResponse(responseCode = "409", description = "ISBN already in use")
    @PostMapping
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest req) {
        Book book = bookService.create(req);
        return ResponseEntity.created(URI.create("/api/books/" + book.getId()))
                .body(BookResponse.from(book));
    }

    @Operation(summary = "Update a book",
            description = "Librarians only. Title, author and total copies can change; the ISBN cannot. "
                    + "Total copies cannot drop below the number currently on loan.")
    @ApiResponse(responseCode = "200", description = "Book updated")
    @ApiResponse(responseCode = "400", description = "Validation failed, or total copies below copies on loan")
    @ApiResponse(responseCode = "403", description = "Only librarians can update books")
    @ApiResponse(responseCode = "404", description = "Book not found")
    @ApiResponse(responseCode = "409", description = "ISBN changed, or the book was updated concurrently")
    @PutMapping("/{id}")
    public BookResponse update(@PathVariable Long id, @Valid @RequestBody BookRequest req) {
        return BookResponse.from(bookService.update(id, req));
    }

    @Operation(summary = "Delete a book", description = "Librarians only. Books with any loan history cannot be deleted.")
    @ApiResponse(responseCode = "204", description = "Book deleted")
    @ApiResponse(responseCode = "403", description = "Only librarians can delete books")
    @ApiResponse(responseCode = "404", description = "Book not found")
    @ApiResponse(responseCode = "409", description = "Book has loan records")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bookService.delete(id);
        return ResponseEntity.noContent().build();
    }
    
}
