package com.example.cherry_be.domain.member.service;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.organization.repository.OrganizationRepository;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * "요청한 기관은 어디이고, 그 기관이 이 피보호자에 무엇을 할 수 있는가" 를 해석한다.
 *
 * 권한이 두 겹이다.
 *  - 조회(getViewable) : 우리 기관에 연결된 피보호자
 *  - 관리(getManaged)  : 그중에서도 기관이 직접 등록한 무연고자만
 *
 * 보호자가 등록하고 기관번호로 연동한 피보호자는 기관이 볼 수는 있어도 고칠 수 없다.
 * 소유자가 보호자이기 때문이다(Member.isManageable).
 *
 * 두 경우 모두 실패를 403 이 아닌 404 로 낸다. 403 은 "있지만 권한이 없다" 를 알려주어
 * id 를 훑으면 존재 여부를 알아낼 수 있게 된다(IDOR). 이 규칙이 서비스마다 흩어지면
 * 어느 하나가 403 을 내도 눈에 띄지 않으므로 여기 한 곳에 둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberFinder {

    private final MemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;

    /** JWT 의 subject(기관 ID)로 기관을 찾는다. */
    public Organization getOrganization(String orgId) {
        return organizationRepository.findByOrgId(orgId)
                .orElseThrow(() -> new CustomException(ErrorCode.ORG_NOT_FOUND));
    }

    public Member getViewable(String orgId, Long targetId) {
        return getViewable(getOrganization(orgId), targetId);
    }

    public Member getManaged(String orgId, Long targetId) {
        return getManaged(getOrganization(orgId), targetId);
    }

    /** 조회 권한 — 우리 기관에 연결돼 있으면 통과. */
    public Member getViewable(Organization organization, Long targetId) {
        Member member = memberRepository.findById(targetId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        if (member.getOrganization() == null
                || !member.getOrganization().getId().equals(organization.getId())) {
            log.warn("타 기관 피보호자 접근 시도 - orgId: {}, targetId: {}",
                    organization.getOrgId(), targetId);
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return member;
    }

    /** 관리 권한 — 조회 권한에 더해, 기관이 직접 등록한 피보호자여야 한다. */
    public Member getManaged(Organization organization, Long targetId) {
        Member member = getViewable(organization, targetId);

        if (!member.isManageable()) {
            log.warn("보호자 소유 피보호자에 대한 기관의 관리 시도 - orgId: {}, targetId: {}",
                    organization.getOrgId(), targetId);
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return member;
    }
}
