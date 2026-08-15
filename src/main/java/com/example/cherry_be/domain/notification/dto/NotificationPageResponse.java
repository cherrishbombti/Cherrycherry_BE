package com.example.cherry_be.domain.notification.dto;

import com.example.cherry_be.domain.notification.entity.Notification;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 알림함 응답. 헤더 뱃지에 쓰이는 미읽음 개수를 함께 내려준다.
 * (목록만으로는 현재 페이지 기준 개수만 알 수 있어 전체 미읽음 수를 별도로 제공)
 */
@Getter
@Builder
public class NotificationPageResponse {

    private long unreadCount;
    private List<NotificationResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;

    public static NotificationPageResponse of(Page<Notification> notifications, long unreadCount) {
        return NotificationPageResponse.builder()
                .unreadCount(unreadCount)
                .content(notifications.getContent().stream()
                        .map(NotificationResponse::from)
                        .toList())
                .page(notifications.getNumber())
                .size(notifications.getSize())
                .totalElements(notifications.getTotalElements())
                .totalPages(notifications.getTotalPages())
                .last(notifications.isLast())
                .build();
    }
}
