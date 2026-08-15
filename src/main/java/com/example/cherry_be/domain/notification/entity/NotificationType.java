package com.example.cherry_be.domain.notification.entity;

/**
 * 알림의 종류(what). 전송 방식(how)과는 다른 축이다.
 * 전송 방식(PUSH/SMS)은 실제 발송 기능을 붙일 때 notification_delivery 로 분리한다.
 */
public enum NotificationType {
    FALL,            // 낙상 감지
    WARNING,         // 주의 상태 전환
    DEVICE_OFFLINE,  // 기기 연결 끊김 (스케줄러 도입 시 사용)
    EMERGENCY        // 119 신고 등 긴급 상황
}
