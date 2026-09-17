package com.example.cherry_be.domain.device.service;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.notification.entity.NotificationType;
import com.example.cherry_be.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceOfflineService {

    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    /**
     * 끊긴 기기를 찾아 알림을 보내고, 보냈다고 표시한다.
     *
     * @return 이번 회차에 새로 알린 건수
     */
    @Transactional
    public int notifyNewlyOffline() {
        List<Member> newlyOffline = memberRepository.findNewlyOffline(Member.offlineDeadline());

        for (Member member : newlyOffline) {
            // 대응하는 fall_log 가 없다. fall_log 는 기기가 보낸 사건의 기록인데
            // 단절은 "보내지 않았다는 사실"이라 남길 사건 자체가 없다.
            // Notification.log 가 nullable 인 이유가 이것이다.
            notificationService.create(member, null, NotificationType.DEVICE_OFFLINE);
            member.markOfflineNotified();

            log.info("기기 끊김 감지 - memberId: {}, deviceMac: {}, 마지막 수신: {}",
                    member.getId(), member.getDeviceMac(), member.getDeviceLastSeen());
        }
        return newlyOffline.size();
    }
}
