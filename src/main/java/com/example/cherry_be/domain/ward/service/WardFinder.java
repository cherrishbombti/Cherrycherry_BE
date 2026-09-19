package com.example.cherry_be.domain.ward.service;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.repository.UserRepository;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.example.cherry_be.global.auth.GuardianEmail;

/**
 * "요청한 보호자는 누구이고, 그의 피보호자는 누구인가" 를 해석한다.
 *
 * 보호자 관점 API 는 전부 이 해석에서 시작한다. 여러 서비스로 나뉘어도 이 규칙만은
 * 한 곳에 있어야 한다. 흩어지면 어느 한 곳에서 조회 실패를 다른 상태코드로 내는 식으로
 * 조용히 어긋나고, 그것이 곧 권한 구멍이 된다.
 */
@Component
@RequiredArgsConstructor
public class WardFinder {

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;

    /** JWT 의 subject(이메일)로 보호자를 찾는다. */
    public User getGuardian(GuardianEmail oauthEmail) {
        return userRepository.findByOauthEmail(oauthEmail.value())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    /** 보호자의 피보호자. 보호자당 1명이다. */
    public Member getWard(User guardian) {
        return memberRepository.findByUser(guardian)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    /** 이메일 한 번으로 피보호자까지. 보호자 자체가 필요 없는 호출부가 쓴다. */
    public Member getWard(GuardianEmail oauthEmail) {
        return getWard(getGuardian(oauthEmail));
    }

    /**
     * 피보호자를 등록했는지 여부. 없는 것이 오류가 아닌 경우에 쓴다.
     * 로그인 직후 등록 화면으로 보낼지 판단하는 데 필요하다.
     */
    public boolean hasWard(User guardian) {
        return memberRepository.findByUser(guardian).isPresent();
    }
}
