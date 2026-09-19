package com.example.cherry_be.domain.device.service;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.service.MemberLivenessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 기기 단절 감지.
 *
 * 낙상·센서고장은 기기가 "보낸 것"을 읽어서 알지만, 단절은 기기가 아무것도 보내지 않아서
 * 알게 된다. 들어오는 요청이 없으니 알려줄 주체가 없고, 따라서 서버가 주기적으로
 * 마지막 수신 시각을 직접 확인하는 수밖에 없다. (DeviceOfflineScheduler 가 호출)
 *
 * 판정 기준은 기기 시계가 아닌 서버 수신 시각이다. 라즈베리파이가 NTP 동기에 실패하면
 * (부팅 직후·네트워크 복구 직후에 흔하다) 기기가 보낸 measured_at 이 엉뚱하게 찍혀
 * 멀쩡한 기기가 끊긴 것으로 잡힌다. Member.deviceLastSeen 주석 참고.
 *
 * 이 클래스에는 트랜잭션이 없다. 한 회차 전체를 한 트랜잭션으로 묶으면 한 건의 실패가
 * 나머지 전원의 알림까지 롤백시키고, offline_notified 도 함께 되돌아가 5초 뒤 같은 배치가
 * 같은 자리에서 다시 실패한다. 실제로 알려야 할 사람들이 영영 알림을 못 받게 되므로
 * 트랜잭션 경계는 DeviceOfflineNotifier 가 피보호자 한 명 단위로 잡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceOfflineService {

    private final MemberLivenessService memberLivenessService;
    private final DeviceOfflineNotifier deviceOfflineNotifier;

    /**
     * 끊긴 기기를 찾아 알림을 보낸다.
     *
     * @return 이번 회차에 실제로 알린 건수
     */
    public int notifyNewlyOffline() {
        LocalDateTime deadline = Member.offlineDeadline();
        List<Long> candidates = memberLivenessService.findNewlyOfflineIds(deadline);

        int notified = 0;
        for (Long memberId : candidates) {
            try {
                if (deviceOfflineNotifier.notifyIfStillOffline(memberId, deadline)) {
                    notified++;
                }
            } catch (Exception e) {
                // 한 명이 실패해도 나머지는 알려야 한다. 다음 회차(5초 뒤)에 이 건만 다시 시도된다.
                log.error("기기 끊김 알림 실패 - memberId: {}", memberId, e);
            }
        }
        return notified;
    }
}
