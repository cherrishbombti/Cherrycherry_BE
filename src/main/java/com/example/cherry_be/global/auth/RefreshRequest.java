package com.example.cherry_be.global.auth;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 앱 전용 재발급 요청 본문(#52).
 *
 * 웹은 리프레시 토큰을 httpOnly 쿠키로 주고받지만,
 * 앱은 커스텀 스킴(cherrishbomb://)으로 리다이렉트되어 Set-Cookie 를 받을 수 없다.
 * 그래서 앱만 본문으로 토큰을 실어 보낸다 — RefreshCookie 참고.
 */
@Getter
@NoArgsConstructor
public class RefreshRequest {

    private String refreshToken;
}
