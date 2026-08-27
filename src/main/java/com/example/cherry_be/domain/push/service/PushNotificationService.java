package com.example.cherry_be.domain.push.service;

import com.example.cherry_be.domain.notification.event.NotificationCreatedEvent;
import com.example.cherry_be.domain.push.dto.PushPayload;
import com.example.cherry_be.domain.push.entity.DeviceToken;
import com.example.cherry_be.domain.push.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 알림 1건을 수신자의 모든 기기로 푸시한다.
 *
 * 알림 저장(NotificationService)과 발송을 분리한 이유
 *  - 발송은 외부 호출이라 느리고 실패할 수 있다
 *  - 저장 트랜잭션 안에서 처리하면 발송 지연이 곧 기기 데이터 수신 지연이 된다
 *
 * 발송은 알림 저장 트랜잭션이 커밋된 뒤에만 시작한다(AFTER_COMMIT).
 * 트랜잭션 안에서 곧바로 비동기 호출하면 커밋 전에 발송되거나,
 * 이후 롤백 시 존재하지 않는 알림에 대한 유령 푸시가 나갈 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final FcmSender fcmSender;

    @Async("pushTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        push(event.payload());
    }

    /**
     * 비동기 발송. 호출부는 결과를 기다리지 않는다.
     */
    private void push(PushPayload payload) {
        try {
            List<DeviceToken> targets = findTargets(payload);
            if (targets.isEmpty()) {
                // 알림함에는 쌓이는데 푸시만 안 오는 상황의 가장 흔한 원인이다.
                // DEBUG 로 두면 기본 로그 레벨에서 안 보여 원인을 찾을 수 없다.
                log.info("등록된 기기가 없어 푸시를 건너뜁니다 - notificationId: {}, userId: {}, organizationId: {}",
                        payload.notificationId(), payload.userId(), payload.organizationId());
                return;
            }
            fcmSender.send(targets, buildTitle(payload), buildBody(payload), buildData(payload));

        } catch (Exception e) {
            // 푸시 실패가 본래 흐름에 영향을 주지 않도록 여기서 삼킨다.
            log.error("푸시 처리 실패 - notificationId: {}, 사유: {}",
                    payload.notificationId(), e.getMessage());
        }
    }

    private List<DeviceToken> findTargets(PushPayload payload) {
        return payload.userId() != null
                ? deviceTokenRepository.findByUserId(payload.userId())
                : deviceTokenRepository.findByOrganizationId(payload.organizationId());
    }

    private String buildTitle(PushPayload payload) {
        return switch (payload.type()) {
            case FALL -> "낙상이 감지되었습니다";
            case WARNING -> "주의가 필요한 상태입니다";
            case DEVICE_OFFLINE -> "기기 연결이 끊겼습니다";
            case EMERGENCY -> "긴급 상황이 발생했습니다";
        };
    }

    /**
     * 알림 문구에는 최소한의 정보만 담는다.
     * 푸시 본문은 외부(FCM) 서버를 경유하고 잠금화면에도 노출되므로,
     * 나이·주소·건강정보 등은 넣지 않고 앱에서 조회하도록 유도한다.
     */
    private String buildBody(PushPayload payload) {
        return payload.memberName() + " 님의 상태를 확인해주세요.";
    }

    /**
     * 클릭 시 화면 이동에 필요한 값. FCM 제약상 값은 모두 문자열이어야 한다.
     */
    private Map<String, String> buildData(PushPayload payload) {
        Map<String, String> data = new HashMap<>();
        data.put("notificationId", String.valueOf(payload.notificationId()));
        data.put("memberId", String.valueOf(payload.memberId()));
        data.put("notificationType", payload.type().name());
        if (payload.logId() != null) {
            data.put("logId", String.valueOf(payload.logId()));
        }
        return data;
    }
}
