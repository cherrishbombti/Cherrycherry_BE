package com.example.cherry_be.domain.ward.dto;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.entity.MemberStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WardSensorResponse {

    private MemberStatus status;      // 전체 상태
    private Boolean vibrator;         // 진동 센서 정상 여부
    private Boolean radar;            // 레이더 센서 정상 여부
    private Boolean thermal;          // 열화상 센서 정상 여부
    private Integer batteryPct;       // 배터리 잔량 (%) - 기기 관리 화면
    private Integer rssi;             // 신호 세기 (dBm, 음수)
    private Boolean deviceOnline;      // 기기 연결 여부 (계산값)
    private LocalDateTime deviceLastSeen; // 마지막 기기 신호 수신 시각 (미수신 시 null)

    public static WardSensorResponse from(Member member) {
        return WardSensorResponse.builder()
                .status(member.getStatus())
                .vibrator(member.getVibrator())
                .radar(member.getRadar())
                .thermal(member.getThermal())
                .batteryPct(member.getBatteryPct())
                .rssi(member.getRssi())
                .deviceOnline(member.isDeviceOnline())
                .deviceLastSeen(member.getDeviceLastSeen())
                .build();
    }
}
