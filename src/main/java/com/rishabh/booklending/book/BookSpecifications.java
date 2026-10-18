package com.rishabh.booklending.book;

import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/** Optional catalogue filters; a blank or missing value means "don't filter on this". */
final class BookSpecifications {

    private BookSpecifications() {
    }

    static Specification<Book> matching(String title, String author, String isbn) {
        Specification<Book> spec = Specification.unrestricted();
        if (StringUtils.hasText(title)) {
            spec = spec.and(containsIgnoreCase("title", title));
        }
        if (StringUtils.hasText(author)) {
            spec = spec.and(containsIgnoreCase("author", author));
        }
        if (StringUtils.hasText(isbn)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("isbn"), isbn.trim()));
        }
        return spec;
    }

    // Partial, case-insensitive match. % and _ typed by the user are matched literally,
    // not treated as LIKE wildcards.
    private static Specification<Book> containsIgnoreCase(String field, String term) {
        String pattern = "%" + escapeLike(term.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(field)), pattern, '\\');
    }

    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
