package com.rishabh.booklending.loan;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    long countByMemberIdAndReturnedAtIsNull(Long memberId);

    boolean existsByMemberIdAndReturnedAtIsNullAndDueDateBefore(Long memberId, Instant now);

    boolean existsByMemberIdAndBookIdAndReturnedAtIsNull(Long memberId, Long bookId);

    boolean existsByBookId(Long bookId);

    boolean existsByMemberId(Long memberId);
    
    @EntityGraph(attributePaths = {"book", "member"})
    Optional<Loan> findById(Long id);

    @EntityGraph(attributePaths = "book")
    Page<Loan> findByMemberId(Long memberId, Pageable pageable);
    boolean existsByIdAndMemberEmail(Long id, String email);
}