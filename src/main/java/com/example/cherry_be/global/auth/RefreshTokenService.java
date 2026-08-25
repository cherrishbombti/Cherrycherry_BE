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

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 만료·폐기된 토큰을 삭제하기 전 남겨두는 기간. 문제 추적 근거로 쓴다. */
    private static final int DEAD_TOKEN_RETENTION_DAYS = 7;

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
                .filter(token -> token.isUsable(now()))
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
            refreshTokenRepository.revokeAllByOrganization(findOrganization(loginId), now());
            return;
        }
        refreshTokenRepository.revokeAllByUser(findUser(loginId), now());
    }

    /**
     * 만료·폐기된 지 보관 기간이 지난 토큰을 삭제한다(#52).
     *
     * 로그인할 때마다 행이 쌓이고 만료돼도 사라지지 않아, 정리하지 않으면 계속 증가한다.
     * 스케줄러가 하루 한 번 호출한다 — RefreshTokenCleanupScheduler 참고.
     *
     * @return 삭제된 행 수
     */
    @Transactional
    public int cleanUpDeadTokens() {
        LocalDateTime cutoff = now().minusDays(DEAD_TOKEN_RETENTION_DAYS);
        return refreshTokenRepository.deleteDeadTokensBefore(cutoff);
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

    /**
     * 이 서비스가 다루는 모든 시각의 단일 출처.
     *
     * JVM 기본 타임존에 기대지 않고 KST 를 명시한다 — Application 이 기동 시
     * TimeZone.setDefault 로 KST 를 잡아 두지만, 그 설정이 사라져도 값이 어긋나지 않게 한다.
     */
    private LocalDateTime now() {
        return LocalDateTime.now(KST);
    }

    private LocalDateTime expiresAt() {
        return now().plus(Duration.ofMillis(refreshExpirationTimeMillis));
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
