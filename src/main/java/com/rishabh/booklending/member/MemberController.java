package com.rishabh.booklending.member;

import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import java.net.URI;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;

@Tag(name = "Members", description = "Manage library members (librarians only, except /me)")
@RestController
@RequestMapping("/api/members")
public class MemberController {
    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @Operation(summary = "Get my member record",
            description = "Any logged-in user. Returns the member whose email is the login username, "
                    + "so a member can find their own id before borrowing.")
    @ApiResponse(responseCode = "200", description = "The caller's member record")
    @ApiResponse(responseCode = "404", description = "The login has no member record (e.g. librarian)")
    @GetMapping("/me")
    public MemberResponse me(Authentication authentication) {
        return MemberResponse.from(memberService.getByLogin(authentication.getName()));
    }

    @Operation(summary = "Get a member by id")
    @ApiResponse(responseCode = "200", description = "Member found")
    @ApiResponse(responseCode = "403", description = "Only librarians can view members")
    @ApiResponse(responseCode = "404", description = "Member not found")
    @GetMapping("/{id}")
    public MemberResponse get(@PathVariable Long id) {
        return MemberResponse.from(memberService.get(id));
    }

    @Operation(summary = "List members", description = "Paginated.")
    @ApiResponse(responseCode = "200", description = "Page of members")
    @ApiResponse(responseCode = "403", description = "Only librarians can list members")
    @GetMapping
    public PagedModel<MemberResponse> list(@ParameterObject Pageable pageable) {
        return new PagedModel<>(memberService.list(pageable).map(MemberResponse::from));
    }


    @Operation(summary = "Add a member", description = "Email must be valid and not already registered.")
    @ApiResponse(responseCode = "201", description = "Member created")
    @ApiResponse(responseCode = "400", description = "Validation failed; see 'errors' in the body")
    @ApiResponse(responseCode = "403", description = "Only librarians can add members")
    @ApiResponse(responseCode = "409", description = "Email already registered")
    @PostMapping
    public ResponseEntity<MemberResponse> create(@Valid @RequestBody MemberRequest request) {
        Member member = memberService.create(request);
        return ResponseEntity.created(URI.create("/api/members/" + member.getId()))
                .body(MemberResponse.from(member));
    }

    @Operation(summary = "Update a member", description = "Email is trimmed and lower-cased before saving.")
    @ApiResponse(responseCode = "200", description = "Member updated")
    @ApiResponse(responseCode = "400", description = "Validation failed; see 'errors' in the body")
    @ApiResponse(responseCode = "403", description = "Only librarians can update members")
    @ApiResponse(responseCode = "404", description = "Member not found")
    @ApiResponse(responseCode = "409", description = "Email already used by a member")
    @PutMapping("/{id}")
    public MemberResponse update(@PathVariable Long id, @Valid @RequestBody MemberRequest request) {
        return MemberResponse.from(memberService.update(id, request));
    }

    @Operation(summary = "Delete a member", description = "Members with any loan history cannot be deleted.")
    @ApiResponse(responseCode = "204", description = "Member deleted")
    @ApiResponse(responseCode = "403", description = "Only librarians can delete members")
    @ApiResponse(responseCode = "404", description = "Member not found")
    @ApiResponse(responseCode = "409", description = "Member has loan records")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        memberService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
