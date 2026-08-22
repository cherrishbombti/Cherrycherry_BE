package com.example.cherry_be.domain.notification.service;

import com.example.cherry_be.domain.log.entity.Log;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.notification.dto.NotificationPageResponse;
import com.example.cherry_be.domain.notification.entity.Notification;
import com.example.cherry_be.domain.notification.entity.NotificationType;
import com.example.cherry_be.domain.notification.event.NotificationCreatedEvent;
import com.example.cherry_be.domain.notification.repository.NotificationRepository;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.push.dto.PushPayload;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림함 조회·읽음 처리 및 알림 생성.
 *
 * 수신자는 보호자(User)와 기관(Organization) 두 종류이며,
 * 피보호자가 어느 쪽에 속하는지에 따라 결정된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ── 보호자 수신함 ────────────────────────────────

    @Transactional(readOnly = true)
    public NotificationPageResponse getNotifications(User user, Pageable pageable) {
        Page<Notification> notifications =
                notificationRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        return NotificationPageResponse.of(
                notifications, notificationRepository.countByUserAndIsReadFalse(user));
    }

    @Transactional
    public void markAsRead(User user, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUser(notificationId, user)
                .orElseThrow(() -> {
                    log.warn("타인의 알림 접근 시도 - userId: {}, notificationId: {}",
                            user.getId(), notificationId);
                    return new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND);
                });
        notification.markAsRead();
    }

    @Transactional
    public int markAllAsRead(User user) {
        return notificationRepository.markAllAsReadByUser(user);
    }

    // ── 기관 수신함 ──────────────────────────────────

    @Transactional(readOnly = true)
    public NotificationPageResponse getNotifications(Organization organization, Pageable pageable) {
        Page<Notification> notifications =
                notificationRepository.findByOrganizationOrderByCreatedAtDesc(organization, pageable);
        return NotificationPageResponse.of(
                notifications, notificationRepository.countByOrganizationAndIsReadFalse(organization));
    }

    @Transactional
    public void markAsRead(Organization organization, Long notificationId) {
        Notification notification =
                notificationRepository.findByIdAndOrganization(notificationId, organization)
                        .orElseThrow(() -> {
                            log.warn("타 기관 알림 접근 시도 - orgId: {}, notificationId: {}",
                                    organization.getId(), notificationId);
                            return new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND);
                        });
        notification.markAsRead();
    }

    @Transactional
    public int markAllAsRead(Organization organization) {
        return notificationRepository.markAllAsReadByOrganization(organization);
    }

    // ── 생성 ────────────────────────────────────────

    /**
     * 알림 생성. 피보호자에 연결된 수신자마다 행을 따로 만든다.
     *
     * 현재는 보호자와 기관이 같은 피보호자를 공유하지 않아 항상 1건만 생성되지만,
     * 향후 두 주체가 함께 관리하게 되면 수신자별로 2건이 생성된다.
     * 한 행을 공유하면 is_read 도 공유되어 한쪽이 읽었을 때 다른 쪽에서도
     * 읽음으로 표시되므로, 처음부터 수신자 단위로 분리해 둔다.
     */
    @Transactional
    public void create(Member member, Log fallLog, NotificationType type) {
        User guardian = member.getUser();
        Organization organization = member.getOrganization();

        if (guardian == null && organization == null) {
            log.warn("수신자가 없어 알림을 생성하지 않음 - memberId: {}", member.getId());
            return;
        }

        if (guardian != null) {
            Notification saved = notificationRepository.save(Notification.builder()
                    .member(member)
                    .user(guardian)
                    .log(fallLog)
                    .notificationType(type)
                    .build());
            eventPublisher.publishEvent(new NotificationCreatedEvent(PushPayload.from(saved)));
        }

        if (organization != null) {
            Notification saved = notificationRepository.save(Notification.builder()
                    .member(member)
                    .organization(organization)
                    .log(fallLog)
                    .notificationType(type)
                    .build());
            eventPublisher.publishEvent(new NotificationCreatedEvent(PushPayload.from(saved)));
        }
    }

    /**
     * 피보호자 삭제 시 함께 정리한다.
     * member_id 가 NOT NULL FK 라 남아있으면 피보호자 삭제 자체가 막힌다.
     */
    @Transactional
    public void deleteByMember(Member member) {
        notificationRepository.deleteByMember(member);
    }
}
