package com.rishabh.booklending.member;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rishabh.booklending.common.ConflictException;
import com.rishabh.booklending.common.NotFoundException;
import com.rishabh.booklending.loan.LoanRepository;

@Service 
public class MemberService {
    private final MemberRepository members;
    private final LoanRepository loans;

    public MemberService(MemberRepository members, LoanRepository loans) {
        this.loans = loans;
        this.members = members;
    }

    @Transactional 
    public Member create(MemberRequest request) {
        if(members.existsByEmail(request.email())) {
            throw new ConflictException("Member with this email already exists.");
        }
        return members.save(new Member(request.name(), request.email()));
    }

    @Transactional(readOnly = true)
    public Member get(Long id) {
        return members.findById(id).orElseThrow(() -> new NotFoundException("Member", id));
    }

    @Transactional(readOnly = true)
    public Page<Member> list(Pageable pageable) {
        return members.findAll(pageable);
    }

    @Transactional 
    public Member update(Long id, MemberRequest request) {
        Member member = get(id);
        String email = request.email().trim().toLowerCase();

        if (!email.equalsIgnoreCase(member.getEmail()) && members.existsByEmail(email)) {
            throw new ConflictException("Email " + email + " is already used by another member.");
        }
        member.updateDetails(request.name(), email); 
        return member;
    }

    @Transactional 
    public void delete(Long id) {
        Member member = get(id);
        if(loans.existsByMemberId(id)) {
            throw new ConflictException("This member cannot be deleted due to loan history.");
        }
        members.delete(member);
    }

}
