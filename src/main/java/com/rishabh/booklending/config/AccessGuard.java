package com.rishabh.booklending.config;

import com.rishabh.booklending.loan.LoanRepository;
import com.rishabh.booklending.member.Member;
import com.rishabh.booklending.member.MemberRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("access")
public class AccessGuard {

    private final MemberRepository members;
    private final LoanRepository loans;

    public AccessGuard(MemberRepository members, LoanRepository loans) {
        this.members = members;
        this.loans = loans;
    }

    public boolean isMember(Authentication auth, Long memberId) {
        return members.existsByIdAndEmail(memberId, Member.normalizeEmail(auth.getName()));
    }

    public boolean ownsLoan(Authentication auth, Long loanId) {
        return loans.existsByIdAndMemberEmail(loanId, Member.normalizeEmail(auth.getName()));
    }
}