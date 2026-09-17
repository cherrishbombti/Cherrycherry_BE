package com.example.cherry_be.domain.device.service;

import com.example.cherry_be.domain.device.dto.DeviceDataRequest;
import com.example.cherry_be.domain.log.entity.Log;
import com.example.cherry_be.domain.log.entity.LogType;
import com.example.cherry_be.domain.log.repository.LogRepository;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.entity.MemberStatus;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.notification.entity.NotificationType;
import com.example.cherry_be.domain.notification.service.NotificationService;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 기기 데이터 수신 로직.
 *
 * payload 를 JSON 으로 만들어 넣는 것은 DTO 가 @JsonProperty 로만 필드를 받기 때문이기도 하고,
 * 기기가 실제로 보내는 형태 그대로 검증하기 위해서이기도 하다.
 */
@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    private static final String DEVICE_ID = "5C:8A:AE:C6:DF:BE";

    @Mock private MemberRepository memberRepository;
    @Mock private LogRepository logRepository;
    @Mock private NotificationService notificationService;

    private DeviceService deviceService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Member member;

    @BeforeEach
    void setUp() {
        deviceService = new DeviceService(memberRepository, logRepository, notificationService);
        member = Member.builder()
                .name("김어르신")
                .deviceMac(DEVICE_ID)
                .build();
        lenient().when(memberRepository.findByDeviceMac(DEVICE_ID)).thenReturn(Optional.of(member));
        lenient().when(logRepository.save(any(Log.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void receive(String json) throws Exception {
        deviceService.receiveDeviceData(objectMapper.readValue(json, DeviceDataRequest.class));
    }

    /** 특정 종류의 로그만 골라낸다. fall_log 테이블에 낙상과 센서고장이 함께 쌓이기 때문. */
    private List<Log> savedLogsOfType(LogType type) {
        ArgumentCaptor<Log> captor = ArgumentCaptor.forClass(Log.class);
        verify(logRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues().stream().filter(l -> l.getLogType() == type).toList();
    }

    @Nested
    @DisplayName("payload 검증")
    class Validation {

        @Test
        @DisplayName("등록되지 않은 device_id 는 거부한다")
        void unknownDevice() {
            when(memberRepository.findByDeviceMac("UNKNOWN")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> receive("""
                    {"device_id":"UNKNOWN","report_type":"HEARTBEAT"}"""))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEVICE_NOT_REGISTERED);
        }

        @Test
        @DisplayName("report_type 이 없으면 거부한다")
        void missingReportType() {
            assertThatThrownBy(() -> receive("""
                    {"device_id":"%s","event_type":"DANGER"}""".formatted(DEVICE_ID)))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REPORT_TYPE);
        }

        @Test
        @DisplayName("EVENT 인데 event_type 이 없으면 거부한다")
        void eventWithoutEventType() {
            assertThatThrownBy(() -> receive("""
                    {"device_id":"%s","report_type":"EVENT"}""".formatted(DEVICE_ID)))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_EVENT_TYPE);
        }

        @Test
        @DisplayName("알 수 없는 event_type 은 거부한다 (기기 내부 상태명이 그대로 오는 경우)")
        void unknownEventType() {
            assertThatThrownBy(() -> receive("""
                    {"device_id":"%s","report_type":"EVENT","event_type":"FALL"}""".formatted(DEVICE_ID)))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_EVENT_TYPE);
        }

        @Test
        @DisplayName("battery_pct 가 0~100 을 벗어나면 거부한다")
        void batteryOutOfRange() {
            assertThatThrownBy(() -> receive("""
                    {"device_id":"%s","report_type":"HEARTBEAT","device":{"battery_pct":120}}"""
                    .formatted(DEVICE_ID)))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("rssi 가 양수면 거부한다 (dBm 은 음수)")
        void positiveRssi() {
            assertThatThrownBy(() -> receive("""
                    {"device_id":"%s","report_type":"HEARTBEAT","device":{"rssi":10}}"""
                    .formatted(DEVICE_ID)))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    @Nested
    @DisplayName("상태 전이와 알림 억제")
    class StatusTransition {

        @Test
        @DisplayName("상태가 나빠지면 로그를 남기고 알린다")
        void worsening() throws Exception {
            receive("""
                    {"device_id":"%s","report_type":"EVENT","event_type":"DANGER"}""".formatted(DEVICE_ID));

            assertThat(savedLogsOfType(LogType.FALL_EVENT)).hasSize(1);
            verify(notificationService).create(eq(member), any(Log.class), eq(NotificationType.FALL));
            assertThat(member.getStatus()).isEqualTo(MemberStatus.DANGER);
        }

        @Test
        @DisplayName("WARNING 은 FALL 이 아닌 WARNING 알림으로 나간다")
        void warningMapsToWarningNotification() throws Exception {
            receive("""
                    {"device_id":"%s","report_type":"EVENT","event_type":"WARNING"}""".formatted(DEVICE_ID));

            verify(notificationService).create(eq(member), any(Log.class), eq(NotificationType.WARNING));
        }

        @Test
        @DisplayName("같은 상태가 반복되면 로그도 알림도 남기지 않는다 (재전송 중복 방지)")
        void sameStatusRepeated() throws Exception {
            String danger = """
                    {"device_id":"%s","report_type":"EVENT","event_type":"DANGER"}""".formatted(DEVICE_ID);
            receive(danger);
            receive(danger);
            receive(danger);

            assertThat(savedLogsOfType(LogType.FALL_EVENT)).hasSize(1);
            verify(notificationService).create(any(), any(), any());
        }

        @Test
        @DisplayName("회복은 로그만 남기고 알리지 않는다")
        void recoveryIsNotNotified() throws Exception {
            receive("""
                    {"device_id":"%s","report_type":"EVENT","event_type":"DANGER"}""".formatted(DEVICE_ID));
            org.mockito.Mockito.clearInvocations(notificationService, logRepository);

            receive("""
                    {"device_id":"%s","report_type":"EVENT","event_type":"SAFE"}""".formatted(DEVICE_ID));

            assertThat(savedLogsOfType(LogType.FALL_EVENT)).hasSize(1);
            verify(notificationService, never()).create(any(), any(), any());
        }

        @Test
        @DisplayName("HEARTBEAT 은 event_type 없이도 받고, 상태를 그대로 둔다")
        void heartbeatKeepsStatus() throws Exception {
            receive("""
                    {"device_id":"%s","report_type":"EVENT","event_type":"WARNING"}""".formatted(DEVICE_ID));
            org.mockito.Mockito.clearInvocations(notificationService, logRepository);

            receive("""
                    {"device_id":"%s","report_type":"HEARTBEAT"}""".formatted(DEVICE_ID));

            assertThat(member.getStatus()).isEqualTo(MemberStatus.WARNING);
            assertThat(savedLogsOfType(LogType.FALL_EVENT)).isEmpty();
            verify(notificationService, never()).create(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("센서 고장 감시")
    class SensorFailure {

        private String heartbeatWith(String radar) {
            return """
                    {"device_id":"%s","report_type":"HEARTBEAT",
                     "sensor_health":{"vibrator":"OK","radar":"%s","thermal":"OK"}}"""
                    .formatted(DEVICE_ID, radar);
        }

        @Test
        @DisplayName("HEARTBEAT 으로도 고장을 기록한다 (EVENT 가 아니어도)")
        void recordedOnHeartbeat() throws Exception {
            receive(heartbeatWith("FAIL"));

            List<Log> logs = savedLogsOfType(LogType.SENSOR_FAILURE);
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getSensorDetail()).isEqualTo("radar");
        }

        @Test
        @DisplayName("고장이 지속되는 동안은 다시 기록하지 않는다")
        void notRecordedWhileFailing() throws Exception {
            receive(heartbeatWith("FAIL"));
            receive(heartbeatWith("FAIL"));
            receive(heartbeatWith("FAIL"));

            assertThat(savedLogsOfType(LogType.SENSOR_FAILURE)).hasSize(1);
        }

        @Test
        @DisplayName("복구 후 다시 고장나면 새 사건으로 기록한다")
        void recordedAgainAfterRecovery() throws Exception {
            receive(heartbeatWith("FAIL"));
            receive(heartbeatWith("OK"));
            receive(heartbeatWith("FAIL"));

            assertThat(savedLogsOfType(LogType.SENSOR_FAILURE)).hasSize(2);
        }

        @Test
        @DisplayName("UNKNOWN 은 고장이 아니므로 기록하지 않는다")
        void unknownIsNotFailure() throws Exception {
            receive(heartbeatWith("UNKNOWN"));

            assertThat(savedLogsOfType(LogType.SENSOR_FAILURE)).isEmpty();
            assertThat(member.getRadar()).isNull();
        }

        @Test
        @DisplayName("센서 3개가 동시에 고장나면 각각 기록한다")
        void allThreeSensors() throws Exception {
            receive("""
                    {"device_id":"%s","report_type":"HEARTBEAT",
                     "sensor_health":{"vibrator":"FAIL","radar":"FAIL","thermal":"FAIL"}}"""
                    .formatted(DEVICE_ID));

            assertThat(savedLogsOfType(LogType.SENSOR_FAILURE))
                    .extracting(Log::getSensorDetail)
                    .containsExactlyInAnyOrder("vibrator", "radar", "thermal");
        }

        @Test
        @DisplayName("sensor_health 블록이 없으면 마지막으로 알던 상태를 유지한다")
        void missingBlockKeepsLastKnown() throws Exception {
            receive(heartbeatWith("FAIL"));
            assertThat(member.getRadar()).isFalse();

            receive("""
                    {"device_id":"%s","report_type":"HEARTBEAT"}""".formatted(DEVICE_ID));

            assertThat(member.getRadar()).isFalse();
            assertThat(savedLogsOfType(LogType.SENSOR_FAILURE)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("수신 시각과 기기 상태")
    class DeviceState {

        @Test
        @DisplayName("수신할 때마다 서버 수신 시각으로 갱신한다 (기기가 보낸 시각이 아니라)")
        void updatesLastSeenWithServerTime() throws Exception {
            LocalDateTime before = LocalDateTime.now();

            receive("""
                    {"device_id":"%s","report_type":"HEARTBEAT",
                     "measured_at":"1999-01-01T00:00:00+09:00"}""".formatted(DEVICE_ID));

            assertThat(member.getDeviceLastSeen())
                    .isNotNull()
                    .isAfterOrEqualTo(before)
                    .isAfter(LocalDateTime.of(2020, 1, 1, 0, 0));
        }

        @Test
        @DisplayName("수신이 재개되면 끊김 알림 표시를 되돌린다")
        void clearsOfflineNotifiedFlag() throws Exception {
            member.markOfflineNotified();

            receive("""
                    {"device_id":"%s","report_type":"HEARTBEAT"}""".formatted(DEVICE_ID));

            assertThat(member).extracting("offlineNotified").isEqualTo(false);
        }

        @Test
        @DisplayName("device 블록이 없으면 마지막 배터리·신호값을 지우지 않는다")
        void keepsLastDeviceMetrics() throws Exception {
            receive("""
                    {"device_id":"%s","report_type":"HEARTBEAT","device":{"battery_pct":85,"rssi":-55}}"""
                    .formatted(DEVICE_ID));
            receive("""
                    {"device_id":"%s","report_type":"HEARTBEAT"}""".formatted(DEVICE_ID));

            assertThat(member.getBatteryPct()).isEqualTo(85);
            assertThat(member.getRssi()).isEqualTo(-55);
        }
    }
}
