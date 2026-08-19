package com.example.cherry_be.domain.push.entity;

/**
 * 푸시를 받을 클라이언트 종류.
 * 서버는 FCM 에 토큰만 넘기면 되므로 발송 자체에는 쓰이지 않지만,
 * 발송 실패 원인 파악과 플랫폼별 선별 발송을 위해 함께 저장한다.
 */
public enum DevicePlatform {
    WEB,      // 브라우저 (사회복지사)
    ANDROID,
    IOS
}
