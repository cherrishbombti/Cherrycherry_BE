package com.example.cherry_be.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        RefreshCookie refreshCookie = new RefreshCookie(true, 1_209_600_000L);
        authController = new AuthController(refreshTokenService, refreshCookie);
    }

    @Test
    void 쿠키의_리프레시_토큰으로_재발급한다() {
        when(refreshTokenService.reissueAccessToken("raw-token")).thenReturn("new-access-token");

        ResponseEntity<Map<String, String>> response = authController.refresh("raw-token", null);

        assertThat(response.getBody()).containsEntry("token", "new-access-token");
    }

    @Test
    void 쿠키가_없으면_본문에서_토큰을_찾는다() {
        RefreshRequest request = new RefreshRequest();
        ReflectionTestUtils.setField(request, "refreshToken", "app-token");
        when(refreshTokenService.reissueAccessToken("app-token")).thenReturn("new-access-token");

        ResponseEntity<Map<String, String>> response = authController.refresh(null, request);

        assertThat(response.getBody()).containsEntry("token", "new-access-token");
    }

    @Test
    void 쿠키가_있으면_본문보다_우선한다() {
        RefreshRequest request = new RefreshRequest();
        ReflectionTestUtils.setField(request, "refreshToken", "app-token");
        when(refreshTokenService.reissueAccessToken("cookie-token")).thenReturn("new-access-token");

        authController.refresh("cookie-token", request);

        verify(refreshTokenService, never()).reissueAccessToken("app-token");
    }

    @Test
    void 쿠키도_본문도_없으면_401을_던진다() {
        assertThatThrownBy(() -> authController.refresh(null, null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFRESH_TOKEN_INVALID);

        verify(refreshTokenService, never()).reissueAccessToken(anyString());
    }

    @Test
    void 빈_문자열_쿠키는_토큰으로_치지_않는다() {
        assertThatThrownBy(() -> authController.refresh("", null))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 만료_쿠키는_값이_비고_수명이_0이다() {
        RefreshCookie refreshCookie = new RefreshCookie(true, 1_209_600_000L);

        assertThat(refreshCookie.expired().getValue()).isEmpty();
        assertThat(refreshCookie.expired().getMaxAge().isZero()).isTrue();
    }

    @Test
    void 쿠키는_httpOnly이고_Path가_api_auth로_제한된다() {
        RefreshCookie refreshCookie = new RefreshCookie(true, 1_209_600_000L);

        var cookie = refreshCookie.create("raw-token");

        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        // Domain을 지정하면 EC2 호스트에 묶여 프록시 뒤에서 쿠키가 저장되지 않는다.
        assertThat(cookie.getDomain()).isNull();
    }

    @Test
    void 로그아웃은_204와_만료_쿠키를_돌려준다() {
        ResponseEntity<Void> response = authController.logout(authentication("ward@example.com", "ROLE_USER"));

        assertThat(response.getStatusCode().value()).isEqualTo(204);

        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        // 값이 비고 Max-Age=0 이어야 브라우저가 쿠키를 즉시 지운다
        assertThat(setCookie).contains("refreshToken=;");
        assertThat(setCookie).contains("Max-Age=0");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("Path=/api/auth");
    }

    @Test
    void 로그아웃은_인증_주체를_그대로_서비스에_넘긴다() {
        Authentication authentication = authentication("org-1", "ROLE_ADMIN");

        authController.logout(authentication);

        // 보호자·기관 분기는 서비스 책임이므로 컨트롤러는 판단하지 않고 넘기기만 한다
        verify(refreshTokenService).revokeAll(authentication);
    }

    private Authentication authentication(String loginId, String role) {
        return new UsernamePasswordAuthenticationToken(
                loginId, null, List.of(new SimpleGrantedAuthority(role)));
    }
}
