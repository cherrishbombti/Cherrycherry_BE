package com.example.cherry_be.domain.log.dto;

import com.example.cherry_be.domain.log.entity.Log;
import com.example.cherry_be.domain.log.entity.LogType;
import com.example.cherry_be.domain.member.entity.MemberStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class LogResponse {

    private Long id;
    private LocalDateTime detectedAt;  // 발생 시각
    private MemberStatus status;       // 당시 상태 (SAFE / WARNING / DANGER)
    private LogType logType;           // FALL_EVENT / SENSOR_FAILURE
    private String sensorDetail;       // 고장 센서명 (SENSOR_FAILURE 시에만, 그 외 null)

    public static LogResponse from(Log log) {
        return LogResponse.builder()
                .id(log.getId())
                .detectedAt(log.getDetectedAt())
                .status(log.getStatus())
                .logType(log.getLogType())
                .sensorDetail(log.getSensorDetail())
                .build();
    }
}
