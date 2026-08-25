package com.example.cherry_be.global.auth;

import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 폐기 가능한 리프레시 토큰.
 *
 * access token은 발급 후 서버가 만료 전에 무효화할 방법이 없다(#52).
 * 값 자체는 저장하지 않고 해시만 저장한다 — DB가 유출돼도 토큰을 재구성할 수 없게 하기 위함.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "refresh_token")
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    // ── 소유자 ────────────────────────────────────────
    // 보호자(users)와 기관(organization)은 서로 다른 테이블이라 단일 FK 로 표현할 수 없다.
    // 한 행에는 소유자가 반드시 하나만 들어간다.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Builder
    public RefreshToken(String tokenHash, LocalDateTime expiresAt, User user, Organization organization) {
        validateSingleOwner(user, organization);
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.user = user;
        this.organization = organization;
    }

    private void validateSingleOwner(User user, Organization organization) {
        if ((user == null) == (organization == null)) {
            throw new IllegalArgumentException(
                    "리프레시 토큰의 소유자는 보호자와 기관 중 정확히 하나여야 합니다.");
        }
    }

    /**
     * 사용 가능 여부. 기준 시각을 인자로 받는다.
     *
     * 엔티티가 스스로 LocalDateTime.now() 를 부르면 JVM 기본 타임존에 의존하게 되어,
     * 만료 시각을 KST 로 계산하는 RefreshTokenService 와 기준이 갈릴 수 있다.
     * 시각의 출처를 서비스 한 곳으로 모으기 위해 밖에서 받는다.
     */
    public boolean isUsable(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(LocalDateTime now) {
        this.revokedAt = now;
    }
}
