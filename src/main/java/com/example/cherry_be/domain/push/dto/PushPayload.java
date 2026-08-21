package com.example.cherry_be.domain.push.dto;

import com.example.cherry_be.domain.notification.entity.Notification;
import com.example.cherry_be.domain.notification.entity.NotificationType;

/**
 * 푸시 발송에 필요한 값만 담은 스냅샷.
 *
 * 엔티티를 그대로 @Async 메서드에 넘기면 트랜잭션 밖에서 LAZY 프록시를 건드리게 되어
 * LazyInitializationException 이 발생한다.
 * 트랜잭션 안에서 필요한 값을 모두 꺼내 전달한다.
 */
public record PushPayload(
        Long notificationId,
        NotificationType type,
        Long memberId,
        String memberName,
        Long logId,
        Long userId,           // 수신자가 보호자면 값이 있음
        Long organizationId    // 수신자가 기관이면 값이 있음
) {

    public static PushPayload from(Notification notification) {
        return new PushPayload(
                notification.getId(),
                notification.getNotificationType(),
                notification.getMember().getId(),
                notification.getMember().getName(),
                notification.getLog() != null ? notification.getLog().getId() : null,
                notification.getUser() != null ? notification.getUser().getId() : null,
                notification.getOrganization() != null ? notification.getOrganization().getId() : null
        );
    }
}
