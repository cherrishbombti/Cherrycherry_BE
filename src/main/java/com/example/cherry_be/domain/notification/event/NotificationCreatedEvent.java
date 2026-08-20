package com.example.cherry_be.domain.notification.event;

import com.example.cherry_be.domain.push.dto.PushPayload;

/**
 * 알림 저장 트랜잭션이 커밋된 후에 푸시를 발송하기 위한 이벤트.
 *
 * NotificationService.create() 안에서 곧바로 @Async push()를 호출하면
 * 트랜잭션 커밋 전에 발송돼버릴 수 있다(롤백 시 유령 알림 발생).
 * 이벤트로 분리해 AFTER_COMMIT 시점에만 리스너가 발송하도록 한다.
 */
public record NotificationCreatedEvent(PushPayload payload) {
}
