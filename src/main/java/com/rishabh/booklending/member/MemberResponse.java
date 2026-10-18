package com.rishabh.booklending.member;

public record MemberResponse(Long id, String name, String email) {
    static MemberResponse from(Member member){
        return new MemberResponse(member.getId(), member.getName(), member.getEmail());
    }
}
