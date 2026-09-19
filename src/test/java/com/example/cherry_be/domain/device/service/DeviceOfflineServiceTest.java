package com.example.cherry_be.domain.device.service;

import com.example.cherry_be.domain.member.service.MemberLivenessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 기기 단절 감지의 회차 진행.
 *
 * "끊긴 대상을 찾는 조건"은 쿼리에 있고 "알릴지 말지"의 판정은 조건부 UPDATE 에 있어
 * 여기서 검증할 수 없다. 여기서는 회차를 어떻게 진행하는지 — 후보를 한 명씩 넘기고,
 * 한 명이 실패해도 나머지를 계속하는지 — 를 본다.
 */
@ExtendWith(MockitoExtension.class)
class DeviceOfflineServiceTest {

    @Mock private MemberLivenessService memberLivenessService;
    @Mock private DeviceOfflineNotifier deviceOfflineNotifier;

    private DeviceOfflineService deviceOfflineService;

    @BeforeEach
    void setUp() {
        deviceOfflineService = new DeviceOfflineService(memberLivenessService, deviceOfflineNotifier);
    }

    @Test
    @DisplayName("후보마다 알림을 시도하고, 실제로 알린 건수를 센다")
    void notifiesEachCandidate() {
        when(memberLivenessService.findNewlyOfflineIds(any())).thenReturn(List.of(1L, 2L));
        when(deviceOfflineNotifier.notifyIfStillOffline(eq(1L), any())).thenReturn(true);
        // 2번은 그 사이 기기가 살아났거나 다른 인스턴스가 이미 가져간 경우
        when(deviceOfflineNotifier.notifyIfStillOffline(eq(2L), any())).thenReturn(false);

        int notified = deviceOfflineService.notifyNewlyOffline();

        assertThat(notified).isEqualTo(1);
        verify(deviceOfflineNotifier).notifyIfStillOffline(eq(1L), any());
        verify(deviceOfflineNotifier).notifyIfStillOffline(eq(2L), any());
    }

    @Test
    @DisplayName("한 명이 실패해도 나머지는 계속 알린다")
    void oneFailureDoesNotStopTheRest() {
        when(memberLivenessService.findNewlyOfflineIds(any())).thenReturn(List.of(1L, 2L, 3L));
        when(deviceOfflineNotifier.notifyIfStillOffline(eq(1L), any()))
                .thenThrow(new RuntimeException("DB 오류"));
        when(deviceOfflineNotifier.notifyIfStillOffline(eq(2L), any())).thenReturn(true);
        when(deviceOfflineNotifier.notifyIfStillOffline(eq(3L), any())).thenReturn(true);

        int notified = deviceOfflineService.notifyNewlyOffline();

        // 회차 전체를 한 트랜잭션으로 묶으면 1번의 실패가 2·3번의 알림까지 롤백시키고,
        // offline_notified 도 되돌아가 5초 뒤 같은 배치가 같은 자리에서 다시 실패한다.
        assertThat(notified).isEqualTo(2);
        verify(deviceOfflineNotifier).notifyIfStillOffline(eq(3L), any());
    }

    @Test
    @DisplayName("끊긴 기기가 없으면 아무것도 하지 않는다")
    void nothingToDo() {
        when(memberLivenessService.findNewlyOfflineIds(any())).thenReturn(List.of());

        assertThat(deviceOfflineService.notifyNewlyOffline()).isZero();
        verify(deviceOfflineNotifier, never()).notifyIfStillOffline(anyLong(), any());
    }

    @Test
    @DisplayName("판정 기준은 기기가 보낸 시각이 아니라 서버 수신 시각이다")
    void usesServerReceiveTime() {
        when(memberLivenessService.findNewlyOfflineIds(any())).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now();

        deviceOfflineService.notifyNewlyOffline();

        // 조회에 넘기는 기준 시각이 "지금으로부터 임계값 이전"인지 확인한다.
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(memberLivenessService).findNewlyOfflineIds(captor.capture());
        assertThat(captor.getValue()).isBefore(before);
    }
}
