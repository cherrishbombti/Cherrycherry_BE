package com.example.cherry_be.domain.health.dto;

import com.example.cherry_be.domain.health.entity.MemberHealth;
import com.example.cherry_be.domain.health.entity.UpdatedByType;
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

    public static HealthResponse from(MemberHealth health) {
        return HealthResponse.builder()
                .disease(health.getDisease())
                .medication(health.getMedication())
                .memo(health.getMemo())
                .updatedByName(health.getUpdatedByName())
                .updatedByType(health.getUpdatedByType())
                .updatedAt(health.getUpdatedAt())
                .build();
    }

    /**
     * 아직 등록된 건강정보가 없을 때. 404 대신 빈 값을 내려 프론트가 그대로 폼에 바인딩할 수 있게 한다.
     */
    public static HealthResponse empty() {
        return HealthResponse.builder().build();
    }
}
