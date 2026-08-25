package com.example.cherry_be.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.organization.repository.OrganizationRepository;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.repository.UserRepository;
import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private JwtUtil jwtUtil;

    private RefreshTokenService refreshTokenService;

    private final User user = User.builder()
            .id(1L)
            .oauthEmail("ward@example.com")
            .name("보호자")
            .oauthProvider(SocialLoginType.GOOGLE)
            .build();

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, userRepository, organizationRepository, jwtUtil);
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpirationTimeMillis", 1_209_600_000L);
    }

    @Test
    void 발급된_토큰은_해시로만_저장된다() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        when(refreshTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = refreshTokenService.issue(user);

        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken).isEqualTo(sha256(rawToken));
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void 유효한_토큰이면_새_access_token을_발급한다() {
        RefreshToken stored = tokenFor("raw-token", LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(jwtUtil.createToken(user.getOauthEmail(), "ROLE_USER")).thenReturn("new-access-token");

        String accessToken = refreshTokenService.reissueAccessToken("raw-token");

        assertThat(accessToken).isEqualTo("new-access-token");
    }

    @Test
    void 만료된_토큰이면_예외를_던진다() {
        RefreshToken expired = tokenFor("raw-token", LocalDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.reissueAccessToken("raw-token"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    @Test
    void 폐기된_토큰이면_예외를_던진다() {
        RefreshToken revoked = tokenFor("raw-token", LocalDateTime.now().plusDays(1));
        revoked.revoke(LocalDateTime.now());
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.reissueAccessToken("raw-token"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 존재하지_않는_토큰이면_예외를_던진다() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.reissueAccessToken("unknown"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 로그아웃시_보호자의_모든_토큰을_폐기한다() {
        when(userRepository.findByOauthEmail("ward@example.com")).thenReturn(Optional.of(user));

        refreshTokenService.revokeAll(authentication("ward@example.com", "ROLE_USER"));

        verify(refreshTokenRepository).revokeAllByUser(eq(user), any(LocalDateTime.class));
    }

    @Test
    void 로그아웃시_기관의_모든_토큰을_폐기한다() {
        Organization organization = Organization.builder()
                .orgId("org-1")
                .password("encoded")
                .name("기관")
                .build();
        when(organizationRepository.findByOrgId("org-1")).thenReturn(Optional.of(organization));

        refreshTokenService.revokeAll(authentication("org-1", "ROLE_ADMIN"));

        verify(refreshTokenRepository).revokeAllByOrganization(eq(organization), any(LocalDateTime.class));
    }

    @Test
    void 로그아웃시_존재하지_않는_계정이면_예외를_던진다() {
        when(userRepository.findByOauthEmail("gone@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                refreshTokenService.revokeAll(authentication("gone@example.com", "ROLE_USER")))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 보관기간이_지난_만료_폐기_토큰을_정리한다() {
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);

        refreshTokenService.cleanUpDeadTokens();

        verify(refreshTokenRepository).deleteDeadTokensBefore(cutoff.capture());
        // 보관 기간 7일 — 오늘보다 과거여야 하고, 살아 있는 토큰을 지울 만큼 최근이면 안 된다
        assertThat(cutoff.getValue()).isBefore(LocalDateTime.now().minusDays(6));
        assertThat(cutoff.getValue()).isAfter(LocalDateTime.now().minusDays(8));
    }

    private Authentication authentication(String loginId, String role) {
        return new UsernamePasswordAuthenticationToken(
                loginId, null, java.util.List.of(new SimpleGrantedAuthority(role)));
    }

    private RefreshToken tokenFor(String rawToken, LocalDateTime expiresAt) {
        return RefreshToken.builder()
                .tokenHash(sha256(rawToken))
                .expiresAt(expiresAt)
                .user(user)
                .build();
    }

    /** RefreshTokenService와 동일한 해시 알고리즘(SHA-256 + Base64)을 검증용으로 재현한다. */
    private String sha256(String rawToken) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
