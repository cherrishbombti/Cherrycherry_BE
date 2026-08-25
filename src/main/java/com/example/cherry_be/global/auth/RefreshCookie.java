package com.example.cherry_be.global.auth;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰을 담는 httpOnly 쿠키(#52).
 *
 * 프론트(Cloudflare Pages)와 백엔드(EC2)는 호스트가 다르지만,
 * 프론트 쪽 프록시를 거쳐 같은 오리진으로 들어오는 것을 전제로 한다.
 * 그래서 SameSite=Lax 로 충분하고, Domain 은 지정하지 않는다 —
 * 지정하면 쿠키가 EC2 호스트에 묶여 브라우저가 저장하지 않는다.
 *
 * Path 를 /api/auth 로 좁혀 재발급·로그아웃 요청에만 쿠키가 실리게 한다.
 * 나머지 API 는 Authorization 헤더만 쓰므로 쿠키가 오갈 이유가 없다.
 * 이 Path 제한과 SameSite=Lax 조합이 CSRF 방어 역할도 한다 —
 * 다른 사이트에서 보낸 POST 에는 Lax 쿠키가 실리지 않는다.
 */
@Component
public class RefreshCookie {

    public static final String NAME = "refreshToken";
    private static final String PATH = "/api/auth";

    private final boolean secure;
    private final Duration maxAge;

    public RefreshCookie(@Value("${auth.refresh-cookie.secure:true}") boolean secure,
                         @Value("${jwt.refresh-expiration-time}") long refreshExpirationTimeMillis) {
        this.secure = secure;
        this.maxAge = Duration.ofMillis(refreshExpirationTimeMillis);
    }

    public ResponseCookie create(String rawToken) {
        return build(rawToken, maxAge);
    }

    /** 로그아웃 시 브라우저에 남은 쿠키를 즉시 지운다. */
    public ResponseCookie expired() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration age) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(PATH)
                .maxAge(age)
                .build();
    }
}
