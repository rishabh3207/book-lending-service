package com.rishabh.booklending.loan;

import com.rishabh.booklending.book.Book;
import com.rishabh.booklending.member.Member;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "loan")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "borrowed_at", nullable = false)
    private Instant borrowedAt;

    @Column(name = "due_date", nullable = false)
    private Instant dueDate;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Loan() {
    }

    public Loan(Member member, Book book, Instant borrowedAt, Instant dueDate) {
        this.member = member;
        this.book = book;
        this.borrowedAt = borrowedAt;
        this.dueDate = dueDate;
    }

    public boolean isOverdue(Instant now) {
        return this.returnedAt == null && dueDate.isBefore(now);
    }

    public void markReturned(Instant now) {
        if (this.returnedAt != null) {
            throw new LoanAlreadyReturnedException(id);
        }
        this.returnedAt = now;
    }

    public Long getId() { return id; }
    public Book getBook() { return book; }
    public Member getMember() { return member; }
    public Instant getBorrowedAt() { return borrowedAt; }
    public Instant getDueDate() { return dueDate; }
    public Instant getReturnedAt() { return returnedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}