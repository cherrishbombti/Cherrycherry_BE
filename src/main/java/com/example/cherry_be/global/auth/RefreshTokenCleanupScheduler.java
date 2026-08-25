package com.example.cherry_be.global.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료·폐기된 리프레시 토큰 정리(#52).
 *
 * 로그인마다 refresh_token 에 행이 쌓이는데 스스로 사라지지 않아,
 * 정리하지 않으면 테이블이 계속 증가한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;

    /**
     * 매일 새벽 4시(KST). 사용자가 가장 적은 시간대라 삭제 잠금이 로그인과 부딪힐 여지가 작다.
     *
     * 서버가 여러 대로 늘어나면 인스턴스마다 실행되어 같은 행을 지우려 경합한다.
     * 삭제는 멱등해서 결과가 틀어지진 않지만, 그때는 분산 락이나 단일 실행 보장이 필요하다.
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void cleanUp() {
        int deleted = refreshTokenService.cleanUpDeadTokens();
        if (deleted > 0) {
            log.info("만료·폐기 리프레시 토큰 {}건 정리", deleted);
        }
    }
}
