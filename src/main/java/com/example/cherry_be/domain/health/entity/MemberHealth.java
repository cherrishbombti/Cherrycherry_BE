package com.example.cherry_be.domain.health.entity;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.global.common.BaseTimeEntity;
import com.example.cherry_be.global.crypto.StringEncryptConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * 피보호자 건강정보 (기저질환·복용약·병력).
 * 민감정보이므로 member_info와 분리해 1:1로 관리한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "member_health")
public class MemberHealth extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 피보호자 1명당 건강정보는 하나만 존재 (UNIQUE로 1:1 보장)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    // ── 민감정보: AES-GCM 으로 암호화해 저장한다 ──
    // 암호문은 Base64 까지 거쳐 원문보다 훨씬 길어진다. 한글은 UTF-8 에서 3바이트라
    // 입력 한도인 255자만 넣어도 암호화 후 1060자가 되므로 여유 있게 2048 로 잡는다.
    // (입력 가능 길이가 늘어난 것이 아니다. 프론트 maxLength 는 255 그대로 유지)
    //
    // TEXT 가 아니라 varchar 인 이유: ddl-auto=update 는 varchar 확장은 자동으로
    // 처리하지만 varchar→TEXT 변환은 하지 않아, TEXT 로 두면 팀원마다 수동 ALTER 가 필요해진다.
    //
    // 저장값은 IV 가 매번 달라 같은 원문이라도 매번 다르다. 검색·정렬·동등비교 불가.

    @Convert(converter = StringEncryptConverter.class)
    @Column(name = "disease", length = 2048)
    private String disease;        // 기저질환

    @Convert(converter = StringEncryptConverter.class)
    @Column(name = "medication", length = 2048)
    private String medication;     // 복용약

    @Convert(converter = StringEncryptConverter.class)
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

    /**
     * 수정 주체만 기록한다. updatedAt 은 BaseTimeEntity 의 Auditing 이 갱신한다.
     * 수정 주체는 users/organization 두 테이블이라 @LastModifiedBy 로 표현할 수 없어
     * 타입+ID 를 직접 저장한다.
     */
    private void markUpdatedBy(UpdatedByType type, Long id, String name) {
        this.updatedByType = type;
        this.updatedById = id;
        this.updatedByName = name;
    }
}
