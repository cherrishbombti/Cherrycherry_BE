package com.example.cherry_be.domain.push.service;

import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.organization.repository.OrganizationRepository;
import com.example.cherry_be.domain.push.dto.DeviceTokenRequest;
import com.example.cherry_be.domain.push.entity.DeviceToken;
import com.example.cherry_be.domain.push.repository.DeviceTokenRepository;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.repository.UserRepository;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FCM 디바이스 토큰 등록·삭제.
 *
 * 보호자와 기관이 모두 푸시를 받으므로, 로그인 주체를 권한(role)으로 구분해 소유자를 결정한다.
 * subject 값이 보호자는 oauthEmail, 기관은 orgId 로 서로 달라 조회 경로도 나뉜다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    /**
     * 토큰 등록. 이미 존재하는 토큰이면 소유자를 갱신한다(upsert).
     *
     * 같은 기기를 다른 계정이 사용할 수 있어(로그아웃 후 재로그인) 단순 저장은 유니크 제약에 걸리고,
     * 무시하면 이전 사용자에게 알림이 계속 간다.
     */
    @Transactional
    public void register(Authentication authentication, DeviceTokenRequest request) {
        boolean isOrganization = hasAdminRole(authentication);
        String loginId = authentication.getName();

        User user = isOrganization ? null : findUser(loginId);
        Organization organization = isOrganization ? findOrganization(loginId) : null;

        deviceTokenRepository.findByToken(request.getToken())
                .ifPresentOrElse(
                        existing -> existing.reassignTo(user, organization, request.getPlatform()),
                        () -> deviceTokenRepository.save(DeviceToken.builder()
                                .token(request.getToken())
                                .platform(request.getPlatform())
                                .user(user)
                                .organization(organization)
                                .build())
                );
    }

    /**
     * 토큰 삭제(로그아웃 시). 이미 없거나 본인 소유가 아니면 조용히 넘어간다.
     *
     * 토큰 값만으로 삭제하면 다른 사람의 토큰 문자열을 아는 것만으로 지울 수 있어,
     * register()와 동일하게 인증 주체가 소유자인 경우에만 삭제한다.
     */
    @Transactional
    public void delete(Authentication authentication, String token) {
        boolean isOrganization = hasAdminRole(authentication);
        String loginId = authentication.getName();

        deviceTokenRepository.findByToken(token).ifPresent(existing -> {
            boolean owned = isOrganization
                    ? existing.getOrganization() != null
                            && existing.getOrganization().getOrgId().equals(loginId)
                    : existing.getUser() != null
                            && existing.getUser().getOauthEmail().equals(loginId);

            if (!owned) {
                log.warn("소유하지 않은 토큰 삭제 시도 - loginId: {}", loginId);
                return;
            }
            deviceTokenRepository.delete(existing);
        });
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
}
