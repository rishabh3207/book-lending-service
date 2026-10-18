package com.rishabh.booklending.book;

import com.rishabh.booklending.common.ConflictException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity 
@Table(name = "book")
public class Book {

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private String isbn;

    @Column(name = "available_copies", nullable = false)
    private int availableCopies;

    @Column(name = "total_copies", nullable = false)
    private int totalCopies;

    @Version 
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Book() {
    }

    public Book(String title, String author, String isbn, int totalCopies) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies;
    }

    public void checkOutCopy() {
        if (availableCopies <= 0) {
            throw new IllegalStateException("No copies available for book " + id);
        }
        availableCopies--;
    }

    public void returnCopy() {
        if (availableCopies >= totalCopies) {
            throw new IllegalStateException("All copies already returned for book " + id);
        }
        availableCopies++;
    }

    public void updateDetails(String title, String author, int newTotalCopies) {
        int onLoan = totalCopies - availableCopies;
        if (newTotalCopies < onLoan) {
            throw new ConflictException(
                "Cannot reduce total copies below " + onLoan + " (currently on loan)");
        }
        this.title = title;
        this.author = author;
        this.totalCopies = newTotalCopies;
        this.availableCopies = newTotalCopies - onLoan;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getIsbn() { return isbn; }
    public int getTotalCopies() { return totalCopies; }
    public int getAvailableCopies() { return availableCopies; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
