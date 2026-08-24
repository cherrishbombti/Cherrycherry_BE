package com.example.cherry_be.domain.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DeviceDataRequest {

    @JsonProperty("device_id")
    private String deviceId;          // 라즈베리파이 고유 ID (device_mac과 매핑)

    @JsonProperty("seq")
    private Long seq;                 // 전송 일련번호 (선택, 중복·순서 판별용)

    @JsonProperty("measured_at")
    private String measuredAt;        // 측정 시각(참고용). 온라인 판정은 서버 수신 시각 사용

    @JsonProperty("report_type")
    private String reportType;        // HEARTBEAT | EVENT

    @JsonProperty("event_type")
    private String eventType;         // SAFE | WARNING | DANGER (EVENT일 때만)

    @JsonProperty("sensor_health")
    private SensorHealth sensorHealth;

    @JsonProperty("device")
    private DeviceInfo device;        // 기기 자체 상태 (선택)

    @Getter
    @NoArgsConstructor
    public static class SensorHealth {
        private String vibrator;      // OK | FAIL | UNKNOWN
        private String radar;         // OK | FAIL | UNKNOWN
        private String thermal;       // OK | FAIL | UNKNOWN
    }

    @Getter
    @NoArgsConstructor
    public static class DeviceInfo {
        @JsonProperty("battery_pct")
        private Integer batteryPct;   // 배터리 잔량 0~100

        private Integer rssi;         // Wi-Fi 신호 세기 dBm (음수)

        @JsonProperty("uptime_sec")
        private Long uptimeSec;        // 마지막 부팅 후 경과 시간(초, 선택)
    }
}
