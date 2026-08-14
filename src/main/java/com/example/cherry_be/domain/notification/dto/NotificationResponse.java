package com.example.cherry_be.domain.notification.dto;

import com.example.cherry_be.domain.notification.entity.Notification;
import com.example.cherry_be.domain.notification.entity.NotificationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationResponse {

    private Long id;
    private NotificationType notificationType; // FALL / WARNING / DEVICE_OFFLINE / EMERGENCY
    private String memberName;                 // 누구에 관한 알림인지
    private Long logId;                        // 연결된 이력 (없으면 null)
    // boolean 필드는 getter 가 isRead() 로 생성되어 Jackson 이 "read" 로 직렬화한다.
    // 프론트 계약이 isRead 이므로 키를 명시한다.
    @JsonProperty("isRead")
    private boolean isRead;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .notificationType(notification.getNotificationType())
                .memberName(notification.getMember().getName())
                .logId(notification.getLog() != null ? notification.getLog().getId() : null)
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
