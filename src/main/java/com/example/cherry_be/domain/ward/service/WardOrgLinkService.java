package com.example.cherry_be.domain.ward.service;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.organization.repository.OrganizationRepository;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.ward.dto.WardOrganizationRequest;
import com.example.cherry_be.domain.ward.dto.WardOrganizationResponse;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기관 연동 (#45). [GET/PATCH/DELETE] /api/wards/me/organization
 *
 * 보호자가 기관번호를 입력하면 피보호자에 기관을 연결한다.
 * 소유자는 그대로 보호자이며, 기관은 대시보드에서 조회만 할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class WardOrgLinkService {

    private final WardFinder wardFinder;
    private final OrganizationRepository organizationRepository;
    private final WardOrgCodeAttemptLimiter orgCodeAttemptLimiter;

    @Transactional(readOnly = true)
    public WardOrganizationResponse getOrganization(String oauthEmail) {
        Member ward = wardFinder.getWard(oauthEmail);
        return ward.getOrganization() == null
                ? WardOrganizationResponse.notLinked()
                : WardOrganizationResponse.from(ward.getOrganization());
    }

    /**
     * 기관 연동. 이미 다른 기관과 연동돼 있으면 새 기관으로 교체한다.
     * 피보호자의 소유자는 보호자이므로 어느 기관에 보일지도 보호자가 정한다.
     *
     * 기관번호는 8자리 숫자뿐이라 무차별 대입 가능성이 있어(리뷰 지적),
     * 계정 기준으로 시도 횟수를 제한한다.
     */
    @Transactional
    public WardOrganizationResponse linkOrganization(String oauthEmail, WardOrganizationRequest request) {
        User guardian = wardFinder.getGuardian(oauthEmail);
        Member ward = wardFinder.getWard(guardian);

        orgCodeAttemptLimiter.assertNotLocked(guardian.getId());

        Organization organization = organizationRepository.findByOrgCode(request.getOrgCode())
                .orElseThrow(() -> {
                    orgCodeAttemptLimiter.recordFailure(guardian.getId());
                    return new CustomException(ErrorCode.ORG_CODE_NOT_FOUND);
                });

        orgCodeAttemptLimiter.reset(guardian.getId());
        ward.linkOrganization(organization);
        return WardOrganizationResponse.from(organization);
    }

    /** 연동 해제. 연동돼 있지 않아도 오류로 보지 않는다(멱등). */
    @Transactional
    public void unlinkOrganization(String oauthEmail) {
        wardFinder.getWard(oauthEmail).unlinkOrganization();
    }
}
