package com.example.cherry_be.domain.device.service;

import com.example.cherry_be.domain.device.dto.DeviceDataRequest;
import com.example.cherry_be.domain.log.entity.Log;
import com.example.cherry_be.domain.log.entity.LogType;
import com.example.cherry_be.domain.log.repository.LogRepository;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.entity.MemberStatus;
import com.example.cherry_be.domain.member.repository.MemberRepository;
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

    @Transactional
    public void receiveDeviceData(DeviceDataRequest request) {

        // 1. device_id로 피보호자 찾기 (없으면 예외)
        Member member = memberRepository.findByDeviceMac(request.getDeviceId())
                .orElseThrow(() -> new CustomException(ErrorCode.DEVICE_NOT_REGISTERED));

        // 2. 문자열 event_type → MemberStatus enum 변환 (DANGER는 EMERGENCY로, 알 수 없는 값은 SAFE로 자동 변환)
        String eventType = request.getEventType();
        if ("DANGER".equals(eventType)) {
            eventType = "EMERGENCY";
        }
        MemberStatus newStatus;
        try {
            newStatus = MemberStatus.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            newStatus = MemberStatus.SAFE;
        }

        DeviceDataRequest.SensorStatus sensorStatus = request.getSensorStatus();
        Boolean vibrator = sensorStatus.getVibrator();
        Boolean radar = sensorStatus.getRadar();
        Boolean thermal = sensorStatus.getThermalImaging();

        // 3. 상태가 변경됐으면 FALL_EVENT 로그 저장
        if (member.getStatus() != newStatus) {
            logRepository.save(Log.builder()
                    .member(member)
                    .organization(member.getOrganization())
                    .status(newStatus)
                    .logType(LogType.FALL_EVENT)
                    .build());
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
}
