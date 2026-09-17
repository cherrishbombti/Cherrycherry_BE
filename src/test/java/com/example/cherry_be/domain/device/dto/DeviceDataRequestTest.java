package com.example.cherry_be.domain.device.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기기가 실제로 보내는 payload 가 DTO 에 그대로 들어오는지 확인한다.
 *
 * 필드명이 하나만 어긋나도 값이 조용히 null 이 되고, 서버는 400 도 내지 않는다.
 * (센서 상태가 전부 "미수신"으로 처리되어 고장 감시가 무력화된다)
 * 눈으로 대조해서는 잡히지 않아 실제 JSON 을 그대로 넣어 고정해 둔다.
 */
class DeviceDataRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("기기가 보내는 EVENT payload 를 모두 읽어낸다")
    void parsesActualDevicePayload() throws Exception {
        // 하드웨어 담당이 확인해 준 실제 전송 형식
        String json = """
                {
                  "device_id": "5C:8A:AE:C6:DF:BE",
                  "report_type": "EVENT",
                  "event_type": "WARNING",
                  "sensor_health": { "vibrator": "OK", "radar": "OK", "thermal": "OK" },
                  "device": { "battery_pct": 85, "rssi": -55 }
                }
                """;

        DeviceDataRequest request = objectMapper.readValue(json, DeviceDataRequest.class);

        assertThat(request.getDeviceId()).isEqualTo("5C:8A:AE:C6:DF:BE");
        assertThat(request.getReportType()).isEqualTo("EVENT");
        assertThat(request.getEventType()).isEqualTo("WARNING");

        // sensor_health 는 중첩 객체라 필드 가시성 때문에 조용히 null 이 되기 쉽다.
        assertThat(request.getSensorHealth()).isNotNull();
        assertThat(request.getSensorHealth().getVibrator()).isEqualTo("OK");
        assertThat(request.getSensorHealth().getRadar()).isEqualTo("OK");
        assertThat(request.getSensorHealth().getThermal()).isEqualTo("OK");

        assertThat(request.getDevice()).isNotNull();
        assertThat(request.getDevice().getBatteryPct()).isEqualTo(85);
        assertThat(request.getDevice().getRssi()).isEqualTo(-55);

        // 기기가 보내지 않는 선택 필드
        assertThat(request.getSeq()).isNull();
        assertThat(request.getMeasuredAt()).isNull();
    }

    @Test
    @DisplayName("HEARTBEAT 은 event_type 없이도 읽히고, sensor_health 는 그대로 실려 온다")
    void parsesHeartbeatPayload() throws Exception {
        String json = """
                {
                  "device_id": "5C:8A:AE:C6:DF:BE",
                  "report_type": "HEARTBEAT",
                  "sensor_health": { "vibrator": "OK", "radar": "FAIL", "thermal": "UNKNOWN" },
                  "device": { "battery_pct": 85, "rssi": -55 }
                }
                """;

        DeviceDataRequest request = objectMapper.readValue(json, DeviceDataRequest.class);

        assertThat(request.getReportType()).isEqualTo("HEARTBEAT");
        assertThat(request.getEventType()).isNull();
        assertThat(request.getSensorHealth().getRadar()).isEqualTo("FAIL");
        assertThat(request.getSensorHealth().getThermal()).isEqualTo("UNKNOWN");
    }
}
