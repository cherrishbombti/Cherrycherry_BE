package com.example.cherry_be.global.auth;

import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.organization.repository.OrganizationRepository;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.repository.UserRepository;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리프레시 토큰 발급·재발급·폐기(#52).
 *
 * access token(JWT)과 달리 리프레시 토큰은 DB에 저장된 상태를 확인하므로 서버가 폐기할 수 있다.
 * 값 자체가 아니라 해시만 저장한다 — RefreshToken 참고.
 *
 * 재발급 시 리프레시 토큰 자체는 새로 발급하지 않는다(회전 없음, 팀 결정).
 * 클라이언트가 리프레시 토큰을 어디서/어떻게 전달하는지(쿠키 vs 바디)는 아직 미정이라
 * 이 서비스는 원문 토큰 문자열만 주고받고, 전달 방식은 컨트롤러 계층에서 결정한다.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String ROLE_USER = "ROLE_USER";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final JwtUtil jwtUtil;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-expiration-time}")
    private long refreshExpirationTimeMillis;

    @Transactional
    public String issue(User user) {
        return issue(hash -> RefreshToken.builder()
                .tokenHash(hash)
                .expiresAt(expiresAt())
                .user(user)
                .build());
    }

    @Transactional
    public String issue(Organization organization) {
        return issue(hash -> RefreshToken.builder()
                .tokenHash(hash)
                .expiresAt(expiresAt())
                .organization(organization)
                .build());
    }

    private String issue(Function<String, RefreshToken> factory) {
        String rawToken = generateRawToken();
        refreshTokenRepository.save(factory.apply(hash(rawToken)));
        return rawToken;
    }

    /**
     * 리프레시 토큰으로 새 access token을 발급한다.
     * 리프레시 토큰 자체는 재사용된다(회전 없음).
     */
    @Transactional
    public String reissueAccessToken(String rawRefreshToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .filter(RefreshToken::isUsable)
                .orElseThrow(() -> new CustomException(ErrorCode.REFRESH_TOKEN_INVALID));

        User user = refreshToken.getUser();
        if (user != null) {
            return jwtUtil.createToken(user.getOauthEmail(), ROLE_USER);
        }
        return jwtUtil.createToken(refreshToken.getOrganization().getOrgId(), ROLE_ADMIN);
    }

    /**
     * 인증 주체의 모든 리프레시 토큰을 폐기한다(로그아웃).
     *
     * 회전을 쓰지 않아 요청자가 들고 있던 토큰 하나만 집어낼 수 없으므로 전부 폐기한다.
     * 보호자·기관 중 어느 쪽인지는 권한으로 가른다 — DeviceTokenService 와 동일한 방식.
     */
    @Transactional
    public void revokeAll(Authentication authentication) {
        String loginId = authentication.getName();

        if (hasAdminRole(authentication)) {
            refreshTokenRepository.revokeAllByOrganization(findOrganization(loginId));
            return;
        }
        refreshTokenRepository.revokeAllByUser(findUser(loginId));
    }

    private boolean hasAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> ROLE_ADMIN.equals(a.getAuthority()));
    }

    private User findUser(String oauthEmail) {
        return userRepository.findByOauthEmail(oauthEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private Organization findOrganization(String orgId) {
        return organizationRepository.findByOrgId(orgId)
                .orElseThrow(() -> new CustomException(ErrorCode.ORG_NOT_FOUND));
    }

    private LocalDateTime expiresAt() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul"))
                .plus(Duration.ofMillis(refreshExpirationTimeMillis));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
