package com.example.cherry_be.domain.push.service;

import com.example.cherry_be.domain.push.entity.DeviceToken;
import com.example.cherry_be.domain.push.repository.DeviceTokenRepository;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * FCM 푸시 발송.
 *
 * 서버는 토큰만 넘기고, 어느 플랫폼으로 보낼지는 FCM 이 판단한다.
 * 따라서 웹·안드로이드·iOS 를 구분하는 분기가 필요 없다.
 *
 * 발송 실패는 절대 호출부로 전파하지 않는다.
 * 푸시는 부가 기능이며, 실패가 낙상 이력 저장 트랜잭션을 롤백시키면 안 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FcmSender {

    private final DeviceTokenRepository deviceTokenRepository;

    // 초기화 실패 시 null 이 주입될 수 있어 required = false
    @Autowired(required = false)
    private FirebaseMessaging firebaseMessaging;

    /**
     * 여러 기기로 동시 발송.
     * 무효 토큰은 삭제한다. 앱 삭제·브라우저 데이터 초기화 등으로 죽은 토큰이 쌓이면
     * 매번 실패 요청을 반복하게 된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(List<DeviceToken> targets, String title, String body, Map<String, String> data) {
        if (firebaseMessaging == null) {
            log.debug("Firebase 미초기화 상태. 푸시를 건너뜁니다.");
            return;
        }
        if (targets.isEmpty()) {
            return;
        }

        for (DeviceToken target : targets) {
            sendOne(target, title, body, data);
        }
    }

    private void sendOne(DeviceToken target, String title, String body, Map<String, String> data) {
        try {
            firebaseMessaging.send(Message.builder()
                    .setToken(target.getToken())
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data)   // 클릭 시 이동에 필요한 값. FCM 제약상 값은 모두 문자열
                    .build());

        } catch (FirebaseMessagingException e) {
            handleFailure(target, e);
        } catch (Exception e) {
            log.error("푸시 발송 중 예기치 못한 오류 - tokenId: {}, 사유: {}", target.getId(), e.getMessage());
        }
    }

    /**
     * 등록되지 않았거나 형식이 잘못된 토큰은 회복 불가이므로 삭제한다.
     * 그 외(네트워크 오류, 서버 일시 장애 등)는 다음 발송 때 재시도되므로 로그만 남긴다.
     */
    private void handleFailure(DeviceToken target, FirebaseMessagingException e) {
        MessagingErrorCode code = e.getMessagingErrorCode();

        if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
            deviceTokenRepository.delete(target);
            log.info("무효 토큰 삭제 - tokenId: {}, code: {}", target.getId(), code);
        } else {
            log.warn("푸시 발송 실패 - tokenId: {}, code: {}, 사유: {}",
                    target.getId(), code, e.getMessage());
        }
    }
}
