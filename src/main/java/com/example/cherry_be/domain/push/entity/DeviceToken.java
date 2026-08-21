package com.example.cherry_be.domain.push.entity;

import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FCM 디바이스 토큰.
 *
 * 서버가 특정 기기로 푸시를 보내려면 그 기기의 "주소"가 필요한데, 그것이 FCM 토큰이다.
 * 인증에 쓰는 JWT 와는 무관하다.
 *
 * 한 사용자가 여러 기기(폰 + 웹)를 쓸 수 있으므로 사용자 : 토큰 = 1 : N.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "device_token")
public class DeviceToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FCM 이 발급하는 값. 기기마다 유일하므로 중복 등록을 막는다.
    @Column(name = "token", nullable = false, unique = true, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false)
    private DevicePlatform platform;

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
    public DeviceToken(String token, DevicePlatform platform, User user, Organization organization) {
        validateSingleOwner(user, organization);
        this.token = token;
        this.platform = platform;
        this.user = user;
        this.organization = organization;
    }

    private void validateSingleOwner(User user, Organization organization) {
        if ((user == null) == (organization == null)) {
            throw new IllegalArgumentException(
                    "토큰 소유자는 보호자와 기관 중 정확히 하나여야 합니다.");
        }
    }

    /**
     * 같은 기기를 다른 계정이 사용하는 경우(로그아웃 후 재로그인 등) 소유자를 옮긴다.
     * 토큰은 기기 단위로 발급되므로 이전 사용자에게 알림이 가는 것을 막아야 한다.
     */
    public void reassignTo(User user, Organization organization, DevicePlatform platform) {
        validateSingleOwner(user, organization);
        this.user = user;
        this.organization = organization;
        this.platform = platform;
    }
}
