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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final MemberRepository memberRepository;
    private final LogRepository logRepository;
    private final NotificationService notificationService;

    @Transactional
    public void receiveDeviceData(DeviceDataRequest request) {

        // 1. device_id로 피보호자 찾기 (없으면 예외)
        Member member = memberRepository.findByDeviceMac(request.getDeviceId())
                .orElseThrow(() -> new CustomException(ErrorCode.DEVICE_NOT_REGISTERED));

        // 2. 문자열 event_type → MemberStatus enum 변환 (잘못된 값이면 400)
        MemberStatus newStatus;
        try {
            newStatus = MemberStatus.valueOf(request.getEventType());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CustomException(ErrorCode.INVALID_EVENT_TYPE);
        }

        DeviceDataRequest.SensorStatus sensorStatus = request.getSensorStatus();
        Boolean vibrator = sensorStatus.getVibrator();
        Boolean radar = sensorStatus.getRadar();
        Boolean thermal = sensorStatus.getThermalImaging();

        // 3. 상태가 변경됐으면 FALL_EVENT 로그 저장 + 보호자에게 알림 생성
        if (member.getStatus() != newStatus) {
            Log fallLog = logRepository.save(Log.builder()
                    .member(member)
                    .organization(member.getOrganization())
                    .status(newStatus)
                    .logType(LogType.FALL_EVENT)
                    .build());

            // SAFE 로 회복되는 것은 알릴 필요가 없으므로 WARNING/DANGER 전환만 알림 대상으로 본다.
            // 수신자(보호자)가 없는 피보호자는 NotificationService 내부에서 건너뛴다.
            if (newStatus != MemberStatus.SAFE) {
                notificationService.create(member, fallLog, toNotificationType(newStatus));
            }
        }

        // 4. 센서 장애 감지 시 SENSOR_FAILURE 로그 저장
        if (Boolean.FALSE.equals(vibrator)) {
            logRepository.save(Log.builder()
                    .member(member)
                    .organization(member.getOrganization())
                    .status(newStatus)
                    .logType(LogType.SENSOR_FAILURE)
                    .sensorDetail("vibrator")
                    .build());
        }
        if (Boolean.FALSE.equals(radar)) {
            logRepository.save(Log.builder()
                    .member(member)
                    .organization(member.getOrganization())
                    .status(newStatus)
                    .logType(LogType.SENSOR_FAILURE)
                    .sensorDetail("radar")
                    .build());
        }
        if (Boolean.FALSE.equals(thermal)) {
            logRepository.save(Log.builder()
                    .member(member)
                    .organization(member.getOrganization())
                    .status(newStatus)
                    .logType(LogType.SENSOR_FAILURE)
                    .sensorDetail("thermal")
                    .build());
        }

        // 5. member_info 최신 상태 업데이트 (항상 실행)
        member.updateFromDevice(newStatus, vibrator, radar, thermal);
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
