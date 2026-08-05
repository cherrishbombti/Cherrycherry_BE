package com.example.cherry_be.domain.log.entity;

public enum LogType {
    FALL_EVENT,      // 낙상 감지 상태 변화 (SAFE→DANGER 등)
    SENSOR_FAILURE,  // 센서 장애 발생
    EMERGENCY_CALL   // 보호자가 119 신고 버튼을 누른 이력 (실제 통화 성공 여부는 알 수 없음)
}
