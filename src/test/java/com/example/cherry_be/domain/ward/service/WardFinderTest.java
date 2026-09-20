package com.example.cherry_be.domain.ward.service;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import com.example.cherry_be.domain.user.repository.UserRepository;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import com.example.cherry_be.global.auth.GuardianEmail;

/**
 * "요청자 → 피보호자" 해석.
 *
 * 보호자 API 전부가 이 해석에서 시작하므로, 실패 시의 에러코드가 여기서 한 번만 정해져야 한다.
 * 서비스마다 제각기 해석하면 어느 하나가 다른 코드를 내도 눈에 띄지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class WardFinderTest {

    private static final String EMAIL = "guardian@example.com";

    @Mock private UserRepository userRepository;
    @Mock private MemberRepository memberRepository;

    @InjectMocks private WardFinder wardFinder;

    private final User guardian = User.builder()
            .id(1L).oauthEmail(EMAIL).name("보호자")
            .oauthProvider(SocialLoginType.GOOGLE).build();

    @Test
    @DisplayName("없는 계정이면 USER_NOT_FOUND")
    void unknownUser() {
        when(userRepository.findByOauthEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wardFinder.getGuardian(new GuardianEmail("ghost@example.com")))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("피보호자를 등록하지 않았으면 MEMBER_NOT_FOUND")
    void noWardRegistered() {
        when(userRepository.findByOauthEmail(EMAIL)).thenReturn(Optional.of(guardian));
        when(memberRepository.findByUser(guardian)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wardFinder.getWard(new GuardianEmail(EMAIL)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("이메일 한 번으로 피보호자까지 찾는다")
    void resolvesWardFromEmail() {
        Member ward = Member.builder().user(guardian).name("김어르신").build();
        when(userRepository.findByOauthEmail(EMAIL)).thenReturn(Optional.of(guardian));
        when(memberRepository.findByUser(guardian)).thenReturn(Optional.of(ward));

        assertThat(wardFinder.getWard(new GuardianEmail(EMAIL))).isEqualTo(ward);
    }

    @Test
    @DisplayName("hasWard 는 없어도 예외를 던지지 않는다 — 로그인 직후 등록 화면 분기에 쓰인다")
    void hasWardDoesNotThrow() {
        when(memberRepository.findByUser(guardian)).thenReturn(Optional.empty());

        assertThat(wardFinder.hasWard(guardian)).isFalse();
    }
}
