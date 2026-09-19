package com.example.cherry_be.domain.device.scheduler;

import com.example.cherry_be.domain.device.service.DeviceOfflineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 기기 단절 감지 스케줄러.
 *
 * 기기는 5초마다 하트비트를 보내지만, 끊기면 아무 요청도 오지 않으므로
 * 수신 경로(DeviceService)에서는 끊김을 알 수 없다. 여기서만 알 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceOfflineScheduler {

    private final DeviceOfflineService deviceOfflineService;

    /**
     * 5초마다. 끊김 판정 기준(20초)보다 충분히 짧아야 한다.
     *
     * 주기가 기준보다 길면 실제 감지 지연은 "기준 + 주기"가 된다.
     * 예를 들어 1분 주기면 20초 기준을 두고도 최대 80초 뒤에야 알림이 나간다.
     *
     * fixedDelay 라 이전 실행이 끝난 뒤 5초를 센다. 대상이 많아 한 회차가 길어져도
     * 실행이 겹치지 않는다. (fixedRate 는 겹칠 수 있다)
     *
     * 서버가 여러 대로 늘어나도 인스턴스마다 실행되는 것 자체는 문제가 되지 않는다.
     * 실제 발송은 MemberRepository.claimOfflineNotification 의 조건부 UPDATE 로
     * 행 단위로 한 번만 확보되므로, 늦게 도착한 쪽은 0을 받고 아무것도 하지 않는다.
     * 중복 실행이 낭비이긴 해서 언젠가 단일 실행 보장이 필요할 수 있지만, 중복 알림은 나가지 않는다.
     */
    @Scheduled(fixedDelay = 5_000)
    public void detectOfflineDevices() {
        int notified = deviceOfflineService.notifyNewlyOffline();
        if (notified > 0) {
            log.info("기기 끊김 알림 {}건 발송", notified);
        }
    }
}
