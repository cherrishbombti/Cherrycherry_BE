package com.example.cherry_be.global.auth;

import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.organization.repository.OrganizationRepository;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.repository.UserRepository;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/auth/refresh 는 리프레시 토큰 전달 방식(쿠키 vs 바디)이 정해진 뒤 추가한다(#52).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

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
        return ResponseEntity.noContent().build();
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
