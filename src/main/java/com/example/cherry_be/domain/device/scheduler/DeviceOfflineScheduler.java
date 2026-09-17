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
     * 서버가 여러 대로 늘어나면 인스턴스마다 실행되어 같은 피보호자에게 중복 알림이
     * 나갈 수 있다. offline_notified 갱신이 먼저 커밋된 쪽이 이기지만 완전한 방어는
     * 아니므로, 그때는 분산 락이나 단일 실행 보장이 필요하다.
     */
    @Scheduled(fixedDelay = 5_000)
    public void detectOfflineDevices() {
        int notified = deviceOfflineService.notifyNewlyOffline();
        if (notified > 0) {
            log.info("기기 끊김 알림 {}건 발송", notified);
        }
    }
}
