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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final MemberRepository memberRepository;
    private final LogRepository logRepository;
    private final NotificationService notificationService;

    @Transactional
    public void receiveDeviceData(DeviceDataRequest request) {

        // 1. device_id로 피보호자 찾기 (없으면 예외)
        // 거부된 값을 남기지 않으면 기기가 무엇을 보냈는지 알 수 없어 패킷을 떠야 한다.
        // 기기 연동 초기에는 형식 불일치(콜론 유무 등)로 이 경로를 자주 타므로 값을 함께 남긴다.
        Member member = memberRepository.findByDeviceMac(request.getDeviceId())
                .orElseThrow(() -> {
                    log.warn("등록되지 않은 device_id - 수신값: '{}'", request.getDeviceId());
                    return new CustomException(ErrorCode.DEVICE_NOT_REGISTERED);
                });

        // report_type 은 HEARTBEAT | EVENT 만 허용 (오타·누락은 400)
        boolean isEvent = parseIsEvent(request.getReportType());

        // 2. 상태 결정: event_type 있으면 파싱, 없으면(HEARTBEAT) 현재 상태 유지
        MemberStatus newStatus = resolveStatus(request, member, isEvent);

        // 3. 센서 상태 OK/FAIL/UNKNOWN → Boolean(true/false/null)
        DeviceDataRequest.SensorHealth sh = request.getSensorHealth();
        Boolean vibrator = toBool(sh == null ? null : sh.getVibrator());
        Boolean radar = toBool(sh == null ? null : sh.getRadar());
        Boolean thermal = toBool(sh == null ? null : sh.getThermal());

        // 4. EVENT일 때만 로그·알림 적재 (HEARTBEAT은 상태 갱신만 → DB 행 안 쌓임)
        if (isEvent) {
            MemberStatus previousStatus = member.getStatus();
            if (previousStatus != newStatus) {
                Log fallLog = logRepository.save(Log.builder()
                        .member(member)
                        .organization(member.getOrganization())
                        .status(newStatus)
                        .logType(LogType.FALL_EVENT)
                        .build());

                // 상태가 나빠질 때만 알린다. (회복은 알리지 않음)
                if (newStatus.isMoreSevereThan(previousStatus)) {
                    notificationService.create(member, fallLog, toNotificationType(newStatus));
                }
            }
            saveSensorFailureLogs(member, newStatus, vibrator, radar, thermal);
        }

        // 5. 배터리·신호 (device 블록 없으면 null → 기존 값 유지)
        Integer batteryPct = request.getDevice() == null ? null : request.getDevice().getBatteryPct();
        Integer rssi = request.getDevice() == null ? null : request.getDevice().getRssi();
        validateDeviceMetrics(batteryPct, rssi);

        // 6. member_info 최신 상태 업데이트 (항상 실행)
        member.updateFromDevice(newStatus, vibrator, radar, thermal, batteryPct, rssi);
    }

    /**
     * event_type 이 있으면 파싱(잘못된 값 400), 없으면 현재 상태 유지.
     * EVENT 인데 event_type 이 없으면 계약 위반이므로 400.
     */
    private MemberStatus resolveStatus(DeviceDataRequest request, Member member, boolean isEvent) {
        String eventType = request.getEventType();
        if (eventType == null || eventType.isBlank()) {
            if (isEvent) {
                throw new CustomException(ErrorCode.INVALID_EVENT_TYPE);
            }
            return member.getStatus();
        }
        try {
            return MemberStatus.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_EVENT_TYPE);
        }
    }
    /** report_type: EVENT → true, HEARTBEAT → false, 그 외·누락 → 400 */
    private boolean parseIsEvent(String reportType) {
        if ("EVENT".equalsIgnoreCase(reportType)) {
            return true;
        }
        if ("HEARTBEAT".equalsIgnoreCase(reportType)) {
            return false;
        }
        throw new CustomException(ErrorCode.INVALID_REPORT_TYPE);
    }

    /** 외부 payload 값 범위 검증. battery_pct 0~100, rssi ≤ 0. */
    private void validateDeviceMetrics(Integer batteryPct, Integer rssi) {
        if (batteryPct != null && (batteryPct < 0 || batteryPct > 100)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (rssi != null && rssi > 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
    /**
     * 센서 상태 문자열 → Boolean.
     * OK → true(정상), FAIL → false(고장), UNKNOWN/null → null(판단 불가).
     * 기존 Member 의 Boolean 3-state(null=미수신) 의미와 그대로 맞물린다.
     */
    private Boolean toBool(String health) {
        if ("OK".equalsIgnoreCase(health)) {
            return true;
        }
        if ("FAIL".equalsIgnoreCase(health)) {
            return false;
        }
        return null;
    }

    private void saveSensorFailureLogs(Member member, MemberStatus status,
                                       Boolean vibrator, Boolean radar, Boolean thermal) {
        if (Boolean.FALSE.equals(vibrator)) {
            saveSensorFailure(member, status, "vibrator");
        }
        if (Boolean.FALSE.equals(radar)) {
            saveSensorFailure(member, status, "radar");
        }
        if (Boolean.FALSE.equals(thermal)) {
            saveSensorFailure(member, status, "thermal");
        }
    }

    private void saveSensorFailure(Member member, MemberStatus status, String sensor) {
        logRepository.save(Log.builder()
                .member(member)
                .organization(member.getOrganization())
                .status(status)
                .logType(LogType.SENSOR_FAILURE)
                .sensorDetail(sensor)
                .build());
    }

    /**
     * 피보호자 상태를 알림 종류로 변환한다.
     * 상태(MemberStatus)와 알림 종류(NotificationType)는 서로 다른 축이라 명시적으로 매핑한다.
     */
    private NotificationType toNotificationType(MemberStatus status) {
        return status == MemberStatus.DANGER
                ? NotificationType.FALL
                : NotificationType.WARNING;
    }
}