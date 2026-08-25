package com.example.cherry_be.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cherry_be.domain.organization.repository.OrganizationRepository;
import com.example.cherry_be.domain.user.repository.UserRepository;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        RefreshCookie refreshCookie = new RefreshCookie(true, 1_209_600_000L);
        authController = new AuthController(
                refreshTokenService, refreshCookie, userRepository, organizationRepository);
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
}
