package com.rishabh.booklending;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rishabh.booklending.book.Book;
import com.rishabh.booklending.book.BookRepository;
import com.rishabh.booklending.member.Member;
import com.rishabh.booklending.member.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalogue and member rules, plus input validation, through the real HTTP layer.
 * Each test runs in a transaction that is rolled back afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class BookAndMemberIntegrationTest {

    private static final RequestPostProcessor LIBRARIAN = httpBasic("librarian", "librarian123");
    private static final RequestPostProcessor RISHABH = httpBasic("rishabh@gmail.com", "password123");

    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired BookRepository books;

    private Member rishabh;
    private Book twoCopies;

    @BeforeEach
    void setUp() {
        rishabh = members.findByEmail("rishabh@gmail.com").orElseThrow();
        twoCopies = books.save(new Book("Refactoring", "Martin Fowler", "9780134757599", 2));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.with(LIBRARIAN).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String book(String title, String isbn, Integer totalCopies) {
        return """
               {"title": "%s", "author": "Author", "isbn": "%s", "totalCopies": %s}
               """.formatted(title, isbn, totalCopies);
    }

    private void lendOneCopy(Book book) throws Exception {
        send(post("/api/loans"), """
                {"memberId": %d, "bookId": %d}
                """.formatted(rishabh.getId(), book.getId()))
                .andExpect(status().isCreated());
    }

    // --- books: validation ---

    @Test
    void newBookStartsWithAllCopiesAvailable() throws Exception {
        send(post("/api/books"), book("Clean Code", "9780132350884", 4))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.totalCopies").value(4))
                .andExpect(jsonPath("$.availableCopies").value(4));
    }

    @Test
    void titleLongerThanTheColumnIsA400NotADatabaseError() throws Exception {
        send(post("/api/books"), book("x".repeat(256), "9780132350884", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void missingTotalCopiesIsReportedAsAFieldError() throws Exception {
        send(post("/api/books"), """
                {"title": "Clean Code", "author": "Robert Martin", "isbn": "9780132350884"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.totalCopies").exists());
    }

    @Test
    void malformedIsbnIsRejected() throws Exception {
        send(post("/api/books"), book("Clean Code", "12345", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.isbn").value("ISBN must be 10 or 13 digits"));
    }

    @Test
    void duplicateIsbnIsAConflict() throws Exception {
        send(post("/api/books"), book("Copy", twoCopies.getIsbn(), 1))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownSortPropertyIsA400NotA500() throws Exception {
        mvc.perform(get("/api/books").param("sort", "price").with(LIBRARIAN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid sort property"));
    }

    @Test
    void pageSizeIsCapped() throws Exception {
        mvc.perform(get("/api/books").param("size", "100000").with(LIBRARIAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(100));
    }

    // --- books: rules ---

    @Test
    void totalCopiesCannotDropBelowCopiesOnLoan() throws Exception {
        lendOneCopy(twoCopies);

        send(put("/api/books/{id}", twoCopies.getId()), book("Refactoring", twoCopies.getIsbn(), 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Cannot reduce total copies below 1 (currently on loan)"));

        send(put("/api/books/{id}", twoCopies.getId()), book("Refactoring", twoCopies.getIsbn(), 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCopies").value(1))
                .andExpect(jsonPath("$.availableCopies").value(0));
    }

    @Test
    void isbnCannotBeChanged() throws Exception {
        send(put("/api/books/{id}", twoCopies.getId()), book("Refactoring", "9780132350884", 2))
                .andExpect(status().isConflict());
    }

    @Test
    void bookWithoutLoansCanBeDeleted() throws Exception {
        mvc.perform(delete("/api/books/{id}", twoCopies.getId()).with(LIBRARIAN))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/books/{id}", twoCopies.getId()).with(LIBRARIAN))
                .andExpect(status().isNotFound());
    }

    @Test
    void bookWithLoanHistoryCannotBeDeleted() throws Exception {
        lendOneCopy(twoCopies);

        mvc.perform(delete("/api/books/{id}", twoCopies.getId()).with(LIBRARIAN))
                .andExpect(status().isConflict());
    }

    // --- books: search ---

    private ResultActions search(String param, String value) throws Exception {
        return mvc.perform(get("/api/books").param(param, value).with(RISHABH)).andExpect(status().isOk());
    }

    @Test
    void searchByTitleIsPartialAndIgnoresCase() throws Exception {
        search("title", "rEfAcT")
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Refactoring"));
    }

    @Test
    void searchByAuthorIsPartialAndIgnoresCase() throws Exception {
        search("author", "kleppmann")
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].isbn").value("9781449373320"));
    }

    @Test
    void searchByIsbnIsExact() throws Exception {
        search("isbn", twoCopies.getIsbn()).andExpect(jsonPath("$.page.totalElements").value(1));
        search("isbn", "978013475").andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void filtersCombineWithAnd() throws Exception {
        mvc.perform(get("/api/books").param("title", "refactoring").param("author", "rowling").with(RISHABH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void likeWildcardsInSearchTermsAreMatchedLiterally() throws Exception {
        search("title", "%").andExpect(jsonPath("$.page.totalElements").value(0));
        search("title", "_").andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void blankFilterIsIgnored() throws Exception {
        long all = books.count();
        search("title", "   ").andExpect(jsonPath("$.page.totalElements").value(all));
    }

    // --- members: me ---

    @Test
    void memberCanLookUpTheirOwnRecord() throws Exception {
        mvc.perform(get("/api/members/me").with(RISHABH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rishabh.getId()))
                .andExpect(jsonPath("$.email").value("rishabh@gmail.com"));
    }

    @Test
    void librarianHasNoMemberRecord() throws Exception {
        mvc.perform(get("/api/members/me").with(LIBRARIAN))
                .andExpect(status().isNotFound());
    }

    @Test
    void meRequiresLogin() throws Exception {
        mvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void membersStillCannotReadOtherMembersById() throws Exception {
        mvc.perform(get("/api/members/{id}", rishabh.getId()).with(RISHABH))
                .andExpect(status().isForbidden());
    }

    // --- members ---

    @Test
    void emailIsStoredLowerCased() throws Exception {
        send(post("/api/members"), """
                {"name": "Asha Rao", "email": "Asha.Rao@Example.COM"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("asha.rao@example.com"));
    }

    @Test
    void sameEmailInDifferentCaseIsAConflict() throws Exception {
        send(post("/api/members"), """
                {"name": "Impostor", "email": "RISHABH@Gmail.com"}
                """)
                .andExpect(status().isConflict());
    }

    @Test
    void memberNameLongerThanTheColumnIsA400() throws Exception {
        send(post("/api/members"), """
                {"name": "%s", "email": "long.name@example.com"}
                """.formatted("n".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void memberWithLoanHistoryCannotBeDeleted() throws Exception {
        lendOneCopy(twoCopies);

        mvc.perform(delete("/api/members/{id}", rishabh.getId()).with(LIBRARIAN))
                .andExpect(status().isConflict());
    }
}
