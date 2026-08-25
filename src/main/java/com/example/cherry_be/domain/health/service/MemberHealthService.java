package com.example.cherry_be.domain.health.service;

import com.example.cherry_be.domain.health.dto.HealthResponse;
import com.example.cherry_be.domain.health.dto.HealthPatchRequest;
import com.example.cherry_be.domain.health.dto.HealthPutRequest;
import com.example.cherry_be.domain.health.entity.MemberHealth;
import com.example.cherry_be.domain.health.entity.UpdatedByType;
import com.example.cherry_be.domain.health.repository.MemberHealthRepository;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 건강정보 공통 로직.
 * 수정 주체(보호자/기관)를 호출부에서 넘겨받아 감사 정보로 기록한다.
 */
@Slf4j
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
     *
     * 복호화에 실패하면 화면 전체를 실패시키는 대신 readable=false 로 내려보낸다.
     * 500 을 던지면 건강정보 화면이 통째로 죽는데, 값을 못 읽는 것은
     * 서버 버그가 아니라 데이터 상태 문제라 사용자에게 상황을 알리는 편이 낫다.
     */
    @Transactional(readOnly = true)
    public HealthResponse get(Member member) {
        try {
            return memberHealthRepository.findByMember(member)
                    .map(HealthResponse::from)
                    .orElseGet(HealthResponse::empty);

        } catch (Exception e) {
            if (!CustomException.has(e, ErrorCode.DECRYPTION_FAILED)) {
                throw e;
            }
            log.error("건강정보 복호화 실패로 읽을 수 없음 - memberId: {}", member.getId());
            return HealthResponse.unreadable();
        }
    }

    /**
     * 수정 전에 기존 행을 읽어온다. 복호화에 실패하면 덮어쓰지 않고 실패시킨다.
     *
     * 자동으로 지우고 새로 쓰지 않는 이유:
     * 복호화 실패가 곧 "복구 불가"를 뜻하지는 않는다. 잘못된 키가 잠시 배포된 상황이라면
     * 원래 키를 되돌리는 것만으로 살아날 데이터다. 그 상태에서 자동 삭제하면
     * 되살릴 수 있었던 민감정보를 영구히 잃는다.
     * 특히 PATCH 는 전달하지 않은 필드를 유지하는 것이 계약인데,
     * 행을 새로 만들면 그 필드들이 null 로 날아간다.
     *
     * 그래서 버릴지 말지는 사용자가 DELETE 로 명시적으로 결정하게 한다.
     */
    private MemberHealth findExistingForUpdate(Member member) {
        try {
            return memberHealthRepository.findByMember(member).orElse(null);

        } catch (Exception e) {
            if (!CustomException.has(e, ErrorCode.DECRYPTION_FAILED)) {
                throw e;
            }
            log.error("복호화 불가 건강정보에 수정 시도 - memberId: {}", member.getId());
            throw new CustomException(ErrorCode.HEALTH_UNREADABLE);
        }
    }

    /**
     * PUT — 없으면 생성, 있으면 전체 교체(upsert).
     */
    @Transactional
    public HealthResponse put(Member member, HealthPutRequest request, Actor actor) {
        MemberHealth health = findExistingForUpdate(member);

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
    public HealthResponse patch(Member member, HealthPatchRequest request, Actor actor) {
        MemberHealth health = findExistingForUpdate(member);

        if (health == null) {
            // 최초 등록: 전달된 필드만 채우고 나머지는 null로 둔다
            health = memberHealthRepository.save(MemberHealth.builder()
                    .member(member)
                    .disease(request.getDisease())
                    .medication(request.getMedication())
                    .memo(request.getMemo())
                    .updatedByType(actor.getType())
                    .updatedById(actor.getId())
                    .updatedByName(actor.getName())
                    .build());
            return HealthResponse.from(health);
        }
        health.patch(request.getDisease(), request.getMedication(), request.getMemo(),
                actor.getType(), actor.getId(), actor.getName());
        return HealthResponse.from(health);
    }

    /**
     * 건강정보 삭제. 사용자의 명시적 삭제 요청과 피보호자 삭제 시 정리에 함께 쓴다.
     *
     * 벌크 삭제라 엔티티를 읽지 않으므로 복호화가 실패하는 행도 지울 수 있다.
     * 값을 못 읽게 된 경우의 유일한 복구 경로이기도 하다.
     * (Member 에 연관관계를 두지 않아 — EAGER 복호화 회피 — cascade 가 걸리지 않는다)
     */
    @Transactional
    public void deleteByMember(Member member) {
        int removed = memberHealthRepository.deleteByMemberInBulk(member);
        log.info("건강정보 삭제 - memberId: {}, 삭제: {}행", member.getId(), removed);
    }
}
