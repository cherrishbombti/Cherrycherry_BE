package com.example.cherry_be.domain.notification.service;

import com.example.cherry_be.domain.log.entity.Log;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.notification.dto.NotificationPageResponse;
import com.example.cherry_be.domain.notification.entity.Notification;
import com.example.cherry_be.domain.notification.entity.NotificationType;
import com.example.cherry_be.domain.notification.repository.NotificationRepository;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // ── 조회 ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public NotificationPageResponse getNotifications(User user, Pageable pageable) {
        Page<Notification> notifications =
                notificationRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        long unreadCount = notificationRepository.countByUserAndIsReadFalse(user);
        return NotificationPageResponse.of(notifications, unreadCount);
    }

    // ── 읽음 처리 ────────────────────────────────────────

    /**
     * 단건 읽음 처리.
     * 조회 시 수신자까지 함께 확인해 타인의 알림에는 접근할 수 없도록 한다.
     * 존재하지 않는 경우와 남의 알림인 경우를 동일하게 404 로 응답해 존재 여부를 노출하지 않는다.
     */
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

    /**
     * 전체 읽음 처리. 건별 UPDATE 를 피하기 위해 벌크 연산을 사용한다.
     */
    @Transactional
    public int markAllAsRead(User user) {
        return notificationRepository.markAllAsReadByUser(user);
    }

    // ── 생성 ────────────────────────────────────────────

    /**
     * 알림 생성.
     *
     * 수신자가 없으면(기관이 등록해 연결된 보호자가 없는 피보호자) 저장하지 않는다.
     * 알림 생성 실패가 본래 흐름(낙상 이력 저장)을 막아서는 안 되므로 호출부에서 예외를 전파하지 않는다.
     */
    @Transactional
    public void create(Member member, Log log, NotificationType type) {
        User receiver = member.getUser();
        if (receiver == null) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .member(member)
                .user(receiver)
                .log(log)
                .notificationType(type)
                .build());
    }
}
