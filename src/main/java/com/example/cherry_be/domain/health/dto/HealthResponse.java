package com.example.cherry_be.domain.health.dto;

import com.example.cherry_be.domain.health.entity.MemberHealth;
import com.example.cherry_be.domain.health.entity.UpdatedByType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class HealthResponse {

    private String disease;               // 기저질환
    private String medication;            // 복용약
    private String memo;                  // 기타 병력
    private String updatedByName;         // 마지막 수정자 이름
    private UpdatedByType updatedByType;  // USER(보호자) / ORGANIZATION(기관)
    private LocalDateTime updatedAt;

    /**
     * 저장된 값을 읽을 수 있었는지.
     *
     * false 면 암호문을 복호화하지 못한 것이다(키 불일치·데이터 손상).
     * "등록된 정보가 없음"과 "저장돼 있으나 읽지 못함"은 전혀 다른 상황인데,
     * 둘 다 빈 값으로 보이면 지병이 있는데도 "기저질환 없음"으로 오인될 수 있어 구분한다.
     */
    @JsonProperty("readable")
    private boolean readable;

    public static HealthResponse from(MemberHealth health) {
        return HealthResponse.builder()
                .disease(health.getDisease())
                .medication(health.getMedication())
                .memo(health.getMemo())
                .updatedByName(health.getUpdatedByName())
                .updatedByType(health.getUpdatedByType())
                .updatedAt(health.getUpdatedAt())
                .readable(true)
                .build();
    }

    /**
     * 아직 등록된 건강정보가 없을 때. 404 대신 빈 값을 내려 프론트가 그대로 폼에 바인딩할 수 있게 한다.
     */
    public static HealthResponse empty() {
        return HealthResponse.builder().readable(true).build();
    }

    /**
     * 저장된 값이 있으나 복호화하지 못했을 때.
     *
     * 이 경우 화면 전체를 실패시키지 않고 "읽을 수 없음"을 명시한다.
     * 값은 복구할 수 없으므로 새로 입력하거나 삭제해야 한다.
     */
    public static HealthResponse unreadable() {
        return HealthResponse.builder().readable(false).build();
    }
}
