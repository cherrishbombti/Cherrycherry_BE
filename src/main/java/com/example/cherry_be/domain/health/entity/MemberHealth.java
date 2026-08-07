package com.example.cherry_be.domain.health.entity;

import com.example.cherry_be.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
     * 전체 수정 (PUT) — 전달된 값으로 모두 교체한다. null이면 값이 비워진다.
     */
    public void replace(String disease, String medication, String memo,
                        UpdatedByType updatedByType, Long updatedById, String updatedByName) {
        this.disease = disease;
        this.medication = medication;
        this.memo = memo;
        markUpdatedBy(updatedByType, updatedById, updatedByName);
    }

    /**
     * 부분 수정 (PATCH) — null인 필드는 기존 값을 유지한다.
     * 값을 비우려면 빈 문자열("")을 전달한다. (프론트와 합의된 규칙)
     */
    public void patch(String disease, String medication, String memo,
                      UpdatedByType updatedByType, Long updatedById, String updatedByName) {
        if (disease != null) this.disease = disease;
        if (medication != null) this.medication = medication;
        if (memo != null) this.memo = memo;
        markUpdatedBy(updatedByType, updatedById, updatedByName);
    }

    private void markUpdatedBy(UpdatedByType type, Long id, String name) {
        this.updatedByType = type;
        this.updatedById = id;
        this.updatedByName = name;
        this.updatedAt = LocalDateTime.now();
    }
}
