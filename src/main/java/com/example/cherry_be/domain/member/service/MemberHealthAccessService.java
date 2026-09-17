package com.example.cherry_be.domain.member.service;

import com.example.cherry_be.domain.health.dto.HealthPatchRequest;
import com.example.cherry_be.domain.health.dto.HealthPutRequest;
import com.example.cherry_be.domain.health.dto.HealthResponse;
import com.example.cherry_be.domain.health.entity.UpdatedByType;
import com.example.cherry_be.domain.health.service.MemberHealthService;
import com.example.cherry_be.domain.organization.entity.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.cherry_be.global.auth.OrgId;

/**
 * 기관이 보는 건강정보 (#26). [GET/PUT/PATCH/DELETE] /api/targets/{targetId}/health
 *
 * 저장·암호화·감사기록은 MemberHealthService 가 하고, 여기서는 권한을 해석해
 * 대상을 좁히고 수정자를 밝히는 일만 한다. 보호자 쪽(WardHealthService)과 대칭이며,
 * 다른 것은 대상 해석 방식뿐이다.
 *
 * 조회는 조회 권한, 수정·삭제는 관리 권한을 요구한다. 보호자가 등록한 피보호자의
 * 건강정보를 기관이 볼 수는 있어도 고칠 수는 없다는 뜻이다.
 */
@Service
@RequiredArgsConstructor
public class MemberHealthAccessService {

    private final MemberFinder memberFinder;
    private final MemberHealthService memberHealthService;

    @Transactional(readOnly = true)
    public HealthResponse getHealth(OrgId orgId, Long targetId) {
        return memberHealthService.get(memberFinder.getViewable(orgId, targetId));
    }

    @Transactional
    public HealthResponse putHealth(OrgId orgId, Long targetId, HealthPutRequest request) {
        Organization organization = memberFinder.getOrganization(orgId);
        return memberHealthService.put(
                memberFinder.getManaged(organization, targetId), request, toActor(organization));
    }

    @Transactional
    public HealthResponse patchHealth(OrgId orgId, Long targetId, HealthPatchRequest request) {
        Organization organization = memberFinder.getOrganization(orgId);
        return memberHealthService.patch(
                memberFinder.getManaged(organization, targetId), request, toActor(organization));
    }

    /**
     * 수정과 같은 권한을 요구한다.
     * 값을 읽지 못하게 된 건강정보를 되돌리는 경로이기도 하다.
     */
    @Transactional
    public void deleteHealth(OrgId orgId, Long targetId) {
        memberHealthService.deleteByMember(memberFinder.getManaged(orgId, targetId));
    }

    private MemberHealthService.Actor toActor(Organization organization) {
        return MemberHealthService.Actor.builder()
                .type(UpdatedByType.ORGANIZATION)
                .id(organization.getId())
                .name(organization.getName())
                .build();
    }
}
