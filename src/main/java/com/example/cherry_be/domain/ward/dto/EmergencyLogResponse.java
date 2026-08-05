package com.example.cherry_be.domain.ward.dto;

import com.example.cherry_be.domain.log.entity.Log;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 119 신고 버튼 클릭 이력 저장 결과.
 * 프론트는 이 응답을 기다리지 않고 즉시 통화를 실행하므로 최소 정보만 반환한다.
 */
@Getter
@Builder
public class EmergencyLogResponse {

    private Long logId;
    private LocalDateTime detectedAt; // 서버 수신 시각

    public static EmergencyLogResponse from(Log log) {
        return EmergencyLogResponse.builder()
                .logId(log.getId())
                .detectedAt(log.getDetectedAt())
                .build();
    }
}
