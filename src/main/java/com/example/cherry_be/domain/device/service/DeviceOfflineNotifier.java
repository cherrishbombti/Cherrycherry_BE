package com.example.cherry_be.domain.device.service;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.service.MemberLivenessService;
import com.example.cherry_be.domain.notification.entity.NotificationType;
import com.example.cherry_be.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 끊김 알림 한 건의 트랜잭션 단위.
 *
 * DeviceOfflineService 에서 분리해 둔 이유는 자기 호출(this.method())로는 프록시를 타지 않아
 * @Transactional 이 걸리지 않기 때문이다. 별도 빈이어야 피보호자 한 명당 한 트랜잭션이 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceOfflineNotifier {

    private final MemberLivenessService memberLivenessService;
    private final NotificationService notificationService;

    /**
     * 여전히 끊겨 있고 아직 아무도 알리지 않았을 때만 알림을 만든다.
     *
     * 먼저 claim 으로 발송 권리를 확보한다. 확보하지 못했다면 다른 인스턴스가 이미 보냈거나
     * 조회 직후 기기가 되살아난 것이므로 아무것도 하지 않는다.
     *
     * claim 과 알림 생성이 한 트랜잭션이라, 알림 생성이 실패하면 claim 도 함께 롤백된다.
     * "보냈다고 표시만 남고 알림은 없는" 상태가 생기지 않는다.
     *
     * @return 실제로 알렸으면 true
     */
    @Transactional
    public boolean notifyIfStillOffline(Long memberId, LocalDateTime deadline) {
        if (!memberLivenessService.claimOfflineNotification(memberId, deadline)) {
            return false;
        }

        Member member = memberLivenessService.getById(memberId);

        // 대응하는 fall_log 가 없다. fall_log 는 기기가 보낸 사건의 기록인데
        // 단절은 "보내지 않았다는 사실"이라 남길 사건 자체가 없다.
        // Notification.log 가 nullable 인 이유가 이것이다.
        notificationService.create(member, null, NotificationType.DEVICE_OFFLINE);

        // 기기 식별자·수신 시각은 남기지 않는다. 장애 추적에는 memberId 로 충분하고,
        // 로그는 알림 본문보다 넓게 퍼지는 경로다.
        log.info("기기 끊김 감지 - memberId: {}", memberId);
        return true;
    }
}
