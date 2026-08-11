package com.example.cherry_be.domain.health.entity;

import com.example.cherry_be.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 피보호자 건강정보 (기저질환·복용약·병력).
 * 민감정보이므로 member_info와 분리해 1:1로 관리한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "member_health")
public class MemberHealth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 피보호자 1명당 건강정보는 하나만 존재 (UNIQUE로 1:1 보장)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @Column(name = "disease")
    private String disease;        // 기저질환

    @Column(name = "medication")
    private String medication;     // 복용약

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;           // 기타 병력

    // ── 감사 추적: 민감정보이므로 마지막 수정 주체를 기록 ──
    @Enumerated(EnumType.STRING)
    @Column(name = "updated_by_type")
    private UpdatedByType updatedByType;

    @Column(name = "updated_by_id")
    private Long updatedById;

    @Column(name = "updated_by_name")
    private String updatedByName;  // 조회 시 조인을 피하기 위해 표시용 이름을 함께 저장

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public MemberHealth(Member member, String disease, String medication, String memo,
                        UpdatedByType updatedByType, Long updatedById, String updatedByName) {
        this.member = member;
        this.disease = disease;
        this.medication = medication;
        this.memo = memo;
        this.updatedByType = updatedByType;
        this.updatedById = updatedById;
        this.updatedByName = updatedByName;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 전체 수정 (PUT) — 전달된 값으로 모두 교체한다.
     * 실제로 바뀐 값이 하나도 없으면 감사 정보(수정자·수정시각)를 갱신하지 않는다.
     */
    public void replace(String disease, String medication, String memo,
                        UpdatedByType updatedByType, Long updatedById, String updatedByName) {
        boolean changed = false;

        if (!Objects.equals(this.disease, disease)) {
            this.disease = disease;
            changed = true;
        }
        if (!Objects.equals(this.medication, medication)) {
            this.medication = medication;
            changed = true;
        }
        if (!Objects.equals(this.memo, memo)) {
            this.memo = memo;
            changed = true;
        }

        if (changed) {
            markUpdatedBy(updatedByType, updatedById, updatedByName);
        }
    }

    /**
     * 부분 수정 (PATCH) — null인 필드는 기존 값을 유지한다.
     * 값을 비우려면 빈 문자열("")을 전달한다. (프론트와 합의된 규칙)
     *
     * 빈 요청({})이나 기존과 동일한 값이 온 경우에는 실제 변경이 없으므로
     * 감사 정보를 갱신하지 않는다. 수정하지 않은 주체가 최종 수정자로
     * 기록되면 감사 추적의 의미가 사라지기 때문이다.
     */
    public void patch(String disease, String medication, String memo,
                      UpdatedByType updatedByType, Long updatedById, String updatedByName) {
        boolean changed = false;

        if (disease != null && !Objects.equals(this.disease, disease)) {
            this.disease = disease;
            changed = true;
        }
        if (medication != null && !Objects.equals(this.medication, medication)) {
            this.medication = medication;
            changed = true;
        }
        if (memo != null && !Objects.equals(this.memo, memo)) {
            this.memo = memo;
            changed = true;
        }

        if (changed) {
            markUpdatedBy(updatedByType, updatedById, updatedByName);
        }
    }

    private void markUpdatedBy(UpdatedByType type, Long id, String name) {
        this.updatedByType = type;
        this.updatedById = id;
        this.updatedByName = name;
        this.updatedAt = LocalDateTime.now();
    }
}
