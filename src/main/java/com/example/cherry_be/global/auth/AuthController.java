package com.example.cherry_be.global.auth;

import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.organization.repository.OrganizationRepository;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.repository.UserRepository;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RefreshTokenService refreshTokenService;
    private final RefreshCookie refreshCookie;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    /**
     * 리프레시 토큰으로 access token 을 재발급한다.
     * [POST] /api/auth/refresh — 인증 불필요(만료된 access token 으로 오는 요청이다)
     *
     * 웹은 쿠키로, 앱은 본문으로 토큰을 보낸다.
     * 회전을 쓰지 않으므로 리프레시 토큰 자체는 그대로 재사용되고, 새 쿠키를 내리지 않는다.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(
            @CookieValue(name = RefreshCookie.NAME, required = false) String cookieToken,
            @RequestBody(required = false) RefreshRequest request) {

        String accessToken = refreshTokenService.reissueAccessToken(resolveToken(cookieToken, request));
        return ResponseEntity.ok(Map.of("token", accessToken));
    }

    /**
     * 인증된 계정의 모든 리프레시 토큰을 폐기한다.
     * 회전을 쓰지 않아 특정 토큰 하나만 집어낼 수 없으므로, 로그아웃 시 전부 폐기한다.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        if (isAdmin(authentication)) {
            Organization organization = organizationRepository.findByOrgId(authentication.getName())
                    .orElseThrow(() -> new CustomException(ErrorCode.ORG_NOT_FOUND));
            refreshTokenService.revokeAll(organization);
        } else {
            User user = userRepository.findByOauthEmail(authentication.getName())
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            refreshTokenService.revokeAll(user);
        }
        // DB 폐기만으로는 브라우저에 쿠키가 남아 다음 재발급 요청에 실려 온다. 함께 지운다.
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.expired().toString())
                .build();
    }

    /** 쿠키(웹)를 우선하고, 없으면 본문(앱)에서 찾는다. */
    private String resolveToken(String cookieToken, RefreshRequest request) {
        if (StringUtils.hasText(cookieToken)) {
            return cookieToken;
        }
        if (request != null && StringUtils.hasText(request.getRefreshToken())) {
            return request.getRefreshToken();
        }
        throw new CustomException(ErrorCode.REFRESH_TOKEN_INVALID);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
