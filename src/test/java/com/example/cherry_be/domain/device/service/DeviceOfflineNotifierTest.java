package com.example.cherry_be.domain.device.service;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.service.MemberLivenessService;
import com.example.cherry_be.domain.notification.entity.NotificationType;
import com.example.cherry_be.domain.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 끊김 알림 한 건.
 *
 * 핵심은 "알림을 보내도 되는가"를 자바가 아니라 DB 가 판정한다는 것이다.
 * claim 이 실패하면(=다른 인스턴스가 먼저 가져갔거나 그 사이 기기가 살아났으면)
 * 알림을 만들지 않는다. 그 분기만 여기서 본다.
 */
@ExtendWith(MockitoExtension.class)
class DeviceOfflineNotifierTest {

    @Mock private MemberLivenessService memberLivenessService;
    @Mock private NotificationService notificationService;

    private DeviceOfflineNotifier deviceOfflineNotifier;
    private Member member;

    @BeforeEach
    void setUp() {
        deviceOfflineNotifier = new DeviceOfflineNotifier(memberLivenessService, notificationService);
        member = Member.builder().name("김어르신").deviceMac("AA:BB:CC:DD:EE:FF").build();
    }

    @Test
    @DisplayName("발송 권리를 확보하면 DEVICE_OFFLINE 알림을 만든다")
    void notifiesWhenClaimed() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(20);
        when(memberLivenessService.claimOfflineNotification(1L, deadline)).thenReturn(true);
        when(memberLivenessService.getById(1L)).thenReturn(member);

        boolean notified = deviceOfflineNotifier.notifyIfStillOffline(1L, deadline);

        assertThat(notified).isTrue();
        // 단절은 기기가 "보내지 않았다는 사실"이라 대응하는 fall_log 가 없다.
        verify(notificationService).create(eq(member), isNull(), eq(NotificationType.DEVICE_OFFLINE));
    }

    @Test
    @DisplayName("권리를 확보하지 못하면 아무것도 하지 않는다")
    void doesNothingWhenNotClaimed() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(20);
        // 다른 인스턴스가 이미 가져갔거나, 조회 직후 하트비트가 들어와 조건이 깨진 경우
        when(memberLivenessService.claimOfflineNotification(1L, deadline)).thenReturn(false);

        boolean notified = deviceOfflineNotifier.notifyIfStillOffline(1L, deadline);

        assertThat(notified).isFalse();
        verify(memberLivenessService, never()).getById(any());
        verify(notificationService, never()).create(any(), any(), any());
    }
}
