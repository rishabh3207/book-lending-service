package com.rishabh.booklending.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rishabh.booklending.common.ConflictException;
import org.junit.jupiter.api.Test;

class BookTest {

    private final Book book = new Book("Refactoring", "Martin Fowler", "9780134757599", 3);

    @Test
    void raisingTotalCopiesAddsAvailableCopiesAndKeepsLoansOut() {
        book.checkOutCopy();
        book.updateDetails("Refactoring 2e", "Martin Fowler", 5);
        assertThat(book.getTotalCopies()).isEqualTo(5);
        assertThat(book.getAvailableCopies()).isEqualTo(4);
    }

    @Test
    void totalCopiesCanDropToExactlyTheNumberOnLoan() {
        book.checkOutCopy();
        book.checkOutCopy();
        book.updateDetails("Refactoring", "Martin Fowler", 2);
        assertThat(book.getAvailableCopies()).isZero();
    }

    @Test
    void totalCopiesCannotDropBelowTheNumberOnLoan() {
        book.checkOutCopy();
        book.checkOutCopy();
        assertThatThrownBy(() -> book.updateDetails("Refactoring", "Martin Fowler", 1))
                .isInstanceOf(ConflictException.class);
        assertThat(book.getTotalCopies()).isEqualTo(3);
    }

    @Test
    void copyCountersNeverLeaveTheirRange() {
        assertThatThrownBy(book::returnCopy).isInstanceOf(IllegalStateException.class);
        book.checkOutCopy();
        book.checkOutCopy();
        book.checkOutCopy();
        assertThatThrownBy(book::checkOutCopy).isInstanceOf(IllegalStateException.class);
        assertThat(book.getAvailableCopies()).isZero();
    }
}
