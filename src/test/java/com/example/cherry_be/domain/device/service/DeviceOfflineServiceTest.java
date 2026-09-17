package com.example.cherry_be.domain.device.service;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.notification.entity.NotificationType;
import com.example.cherry_be.domain.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 기기 단절 감지.
 *
 * "끊긴 대상을 찾는 조건"은 쿼리(MemberRepository.findNewlyOffline)에 있어 여기서 검증할 수 없다.
 * 여기서는 찾은 뒤의 처리 — 알림 1회 발송과 재발송 차단 — 를 본다.
 */
@ExtendWith(MockitoExtension.class)
class DeviceOfflineServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private NotificationService notificationService;

    private DeviceOfflineService deviceOfflineService;
    private Member member;

    @BeforeEach
    void setUp() {
        deviceOfflineService = new DeviceOfflineService(memberRepository, notificationService);
        member = Member.builder().name("김어르신").deviceMac("AA:BB:CC:DD:EE:FF").build();
    }

    @Test
    @DisplayName("끊긴 기기마다 DEVICE_OFFLINE 알림을 만든다")
    void notifiesOfflineDevices() {
        when(memberRepository.findNewlyOffline(any())).thenReturn(List.of(member));

        int notified = deviceOfflineService.notifyNewlyOffline();

        assertThat(notified).isEqualTo(1);
        verify(notificationService).create(eq(member), isNull(), eq(NotificationType.DEVICE_OFFLINE));
    }

    @Test
    @DisplayName("알린 뒤에는 표시를 남겨 다음 회차에 다시 뽑히지 않게 한다")
    void marksAsNotified() {
        when(memberRepository.findNewlyOffline(any())).thenReturn(List.of(member));

        deviceOfflineService.notifyNewlyOffline();

        // 이 표시가 없으면 5초마다 같은 알림이 반복된다.
        assertThat(member).extracting("offlineNotified").isEqualTo(true);
    }

    @Test
    @DisplayName("끊긴 기기가 없으면 아무것도 하지 않는다")
    void nothingToDo() {
        when(memberRepository.findNewlyOffline(any())).thenReturn(List.of());

        assertThat(deviceOfflineService.notifyNewlyOffline()).isZero();
        verify(notificationService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("판정 기준은 기기가 보낸 시각이 아니라 서버 수신 시각이다")
    void usesServerReceiveTime() {
        when(memberRepository.findNewlyOffline(any())).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now();

        deviceOfflineService.notifyNewlyOffline();

        // 조회에 넘기는 기준 시각이 "지금으로부터 임계값 이전"인지 확인한다.
        org.mockito.ArgumentCaptor<LocalDateTime> captor =
                org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(memberRepository).findNewlyOffline(captor.capture());
        assertThat(captor.getValue()).isBefore(before);
    }
}
