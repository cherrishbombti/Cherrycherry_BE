package com.example.cherry_be.domain.health.service;

import com.example.cherry_be.domain.health.dto.HealthResponse;
import com.example.cherry_be.domain.health.dto.HealthUpsertRequest;
import com.example.cherry_be.domain.health.entity.MemberHealth;
import com.example.cherry_be.domain.health.entity.UpdatedByType;
import com.example.cherry_be.domain.health.repository.MemberHealthRepository;
import com.example.cherry_be.domain.member.entity.Member;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 건강정보 공통 로직.
 * 수정 주체(보호자/기관)를 호출부에서 넘겨받아 감사 정보로 기록한다.
 */
@Service
@RequiredArgsConstructor
public class MemberHealthService {

    private final MemberHealthRepository memberHealthRepository;

    /** 수정 주체 정보 */
    @Getter
    @Builder
    public static class Actor {
        private UpdatedByType type;
        private Long id;
        private String name;
    }

    /**
     * 조회 — 아직 등록 전이면 빈 응답을 반환한다(404 아님).
     */
    @Transactional(readOnly = true)
    public HealthResponse get(Member member) {
        return memberHealthRepository.findByMember(member)
                .map(HealthResponse::from)
                .orElseGet(HealthResponse::empty);
    }

    /**
     * PUT — 없으면 생성, 있으면 전체 교체(upsert).
     */
    @Transactional
    public HealthResponse put(Member member, HealthUpsertRequest request, Actor actor) {
        MemberHealth health = memberHealthRepository.findByMember(member).orElse(null);

        if (health == null) {
            health = memberHealthRepository.save(MemberHealth.builder()
                    .member(member)
                    .disease(request.getDisease())
                    .medication(request.getMedication())
                    .memo(request.getMemo())
                    .updatedByType(actor.getType())
                    .updatedById(actor.getId())
                    .updatedByName(actor.getName())
                    .build());
        } else {
            health.replace(request.getDisease(), request.getMedication(), request.getMemo(),
                    actor.getType(), actor.getId(), actor.getName());
        }
        return HealthResponse.from(health);
    }

    /**
     * PATCH — 전달된 필드만 수정. 없으면 생성한다.
     */
    @Transactional
    public HealthResponse patch(Member member, HealthUpsertRequest request, Actor actor) {
        MemberHealth health = memberHealthRepository.findByMember(member).orElse(null);

        if (health == null) {
            // 최초 등록이면 PUT과 동일하게 처리
            return put(member, request, actor);
        }
        health.patch(request.getDisease(), request.getMedication(), request.getMemo(),
                actor.getType(), actor.getId(), actor.getName());
        return HealthResponse.from(health);
    }
}
