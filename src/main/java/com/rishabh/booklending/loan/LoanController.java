package com.rishabh.booklending.loan;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import java.net.URI;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;

@Tag(name = "Loans", description = "Borrow and return books")
@RestController 
@RequestMapping("/api")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @Operation(summary = "Borrow a book",
            description = "Members may only borrow for themselves. Rejected if the member has an overdue loan, "
                    + "has reached the active-loan limit, already holds this book, or no copies are available.")
    @ApiResponse(responseCode = "201", description = "Loan created")
    @ApiResponse(responseCode = "403", description = "Not allowed to borrow for this member")
    @ApiResponse(responseCode = "404", description = "Member or book not found")
    @ApiResponse(responseCode = "409", description = "Borrowing rule violated; see 'reason' in the body")
    @PreAuthorize("hasRole('LIBRARIAN') or @access.isMember(authentication, #request.memberId())")
    @PostMapping("/loans")
    public ResponseEntity<LoanResponse> borrow(@Valid @RequestBody  LoanRequest request) {
        Loan loan = loanService.borrow(request.memberId(), request.bookId());
        return ResponseEntity.created(URI.create("/api/loans/" + loan.getId())).body(LoanResponse.from(loan));
    }

    @Operation(summary = "Return a borrowed book")
    @ApiResponse(responseCode = "200", description = "Loan returned")
    @ApiResponse(responseCode = "409", description = "Loan was already returned")
    @PreAuthorize("hasRole('LIBRARIAN') or @access.ownsLoan(authentication, #id)")
    @PostMapping("/loans/{id}/return")
    public LoanResponse returnLoan(@PathVariable Long id) {
        return LoanResponse.from(loanService.returnLoan(id));
    }

    @Operation(summary = "List a member's loans", description = "Paginated; members can only see their own.")
    @PreAuthorize("hasRole('LIBRARIAN') or @access.isMember(authentication, #memberId)")
    @GetMapping("/members/{memberId}/loans")
    public PagedModel<LoanResponse> memberLoans(@PathVariable Long memberId, @ParameterObject Pageable pageable) {
        return new PagedModel<>(loanService.loansOfMember(memberId, pageable).map(LoanResponse::from));
    }
}
