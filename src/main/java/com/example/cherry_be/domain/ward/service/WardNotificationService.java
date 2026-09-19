package com.example.cherry_be.domain.ward.service;

import com.example.cherry_be.domain.notification.dto.NotificationPageResponse;
import com.example.cherry_be.domain.notification.service.NotificationService;
import com.example.cherry_be.domain.ward.dto.UnreadCountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.cherry_be.global.auth.GuardianEmail;

/**
 * 보호자 알림함 (#25). [GET/PATCH] /api/wards/me/notifications
 *
 * 알림은 피보호자가 아니라 보호자 계정에 달린다. 그래서 여기서는 피보호자를 찾지 않는다.
 * 피보호자를 아직 등록하지 않았어도 알림함은 열려야 한다.
 */
@Service
@RequiredArgsConstructor
public class WardNotificationService {

    private final WardFinder wardFinder;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public NotificationPageResponse getNotifications(GuardianEmail oauthEmail, Pageable pageable) {
        return notificationService.getNotifications(wardFinder.getGuardian(oauthEmail), pageable);
    }

    @Transactional
    public void readNotification(GuardianEmail oauthEmail, Long notificationId) {
        notificationService.markAsRead(wardFinder.getGuardian(oauthEmail), notificationId);
    }

    @Transactional
    public int readAllNotifications(GuardianEmail oauthEmail) {
        return notificationService.markAllAsRead(wardFinder.getGuardian(oauthEmail));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(GuardianEmail oauthEmail) {
        return new UnreadCountResponse(
                notificationService.getUnreadCount(wardFinder.getGuardian(oauthEmail)));
    }
}
