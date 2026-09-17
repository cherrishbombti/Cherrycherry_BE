package com.example.cherry_be.domain.ward.service;

import com.example.cherry_be.domain.health.dto.HealthPatchRequest;
import com.example.cherry_be.domain.health.dto.HealthPutRequest;
import com.example.cherry_be.domain.health.dto.HealthResponse;
import com.example.cherry_be.domain.health.entity.UpdatedByType;
import com.example.cherry_be.domain.health.service.MemberHealthService;
import com.example.cherry_be.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.cherry_be.global.auth.GuardianEmail;

/**
 * 보호자가 보는 건강정보 (#26). [GET/PUT/PATCH/DELETE] /api/wards/me/health
 *
 * 저장·암호화·감사기록은 MemberHealthService 가 하고, 여기서는
 * "요청한 보호자의 피보호자" 로 대상을 좁히고 수정자를 밝히는 일만 한다.
 * 기관 쪽(MemberService)도 같은 MemberHealthService 를 쓰며, 다른 것은 이 대상 해석뿐이다.
 */
@Service
@RequiredArgsConstructor
public class WardHealthService {

    private final WardFinder wardFinder;
    private final MemberHealthService memberHealthService;

    @Transactional(readOnly = true)
    public HealthResponse getHealth(GuardianEmail oauthEmail) {
        return memberHealthService.get(wardFinder.getWard(oauthEmail));
    }

    @Transactional
    public HealthResponse putHealth(GuardianEmail oauthEmail, HealthPutRequest request) {
        User guardian = wardFinder.getGuardian(oauthEmail);
        return memberHealthService.put(wardFinder.getWard(guardian), request, toActor(guardian));
    }

    @Transactional
    public HealthResponse patchHealth(GuardianEmail oauthEmail, HealthPatchRequest request) {
        User guardian = wardFinder.getGuardian(oauthEmail);
        return memberHealthService.patch(wardFinder.getWard(guardian), request, toActor(guardian));
    }

    /** 값을 읽지 못하게 된 건강정보를 되돌리는 경로이기도 하다. */
    @Transactional
    public void deleteHealth(GuardianEmail oauthEmail) {
        memberHealthService.deleteByMember(wardFinder.getWard(oauthEmail));
    }

    /** 누가 고쳤는지 남기기 위한 정보. 보호자와 기관이 같은 테이블을 고치므로 타입을 함께 넘긴다. */
    private MemberHealthService.Actor toActor(User guardian) {
        return MemberHealthService.Actor.builder()
                .type(UpdatedByType.USER)
                .id(guardian.getId())
                .name(guardian.getName())
                .build();
    }
}
