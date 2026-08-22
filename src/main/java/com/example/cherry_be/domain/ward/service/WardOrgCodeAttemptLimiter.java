package com.example.cherry_be.domain.ward.service;

import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 기관번호 연동 시도 횟수 제한.
 *
 * 기관번호가 8자리 숫자뿐이라 무차별 대입에 완전히 안전하지 않다.
 * 보호자 계정 기준으로 제한한다 — 이 엔드포인트는 이미 인증이 걸려있어
 * 호출 전에 계정이 확정되고, IP 기준보다 우회가 훨씬 비싸다
 * (계정 하나 만드는 데 OAuth 로그인이 필요함).
 *
 * 서버가 1대뿐이라 인메모리로 충분하다. 다중 인스턴스로 늘어나면
 * Redis 등 공유 저장소로 옮겨야 한다.
 */
@Component
public class WardOrgCodeAttemptLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();

    /** 연동 시도 전에 호출. 창이 꽉 찼으면 예외. */
    public void assertNotLocked(Long guardianId) {
        Window window = windows.get(guardianId);
        if (window != null && window.isActive() && window.count >= MAX_ATTEMPTS) {
            throw new CustomException(ErrorCode.ORG_CODE_TOO_MANY_ATTEMPTS);
        }
    }

    /** 기관번호가 틀렸을 때 호출. */
    public void recordFailure(Long guardianId) {
        windows.compute(guardianId, (id, window) ->
                (window == null || !window.isActive())
                        ? new Window(Instant.now(), 1)
                        : new Window(window.startedAt(), window.count() + 1));
    }

    /** 연동 성공 시 호출 — 정상 사용자는 실수로 틀린 이력이 남지 않도록 즉시 해제한다. */
    public void reset(Long guardianId) {
        windows.remove(guardianId);
    }

    private record Window(Instant startedAt, int count) {
        boolean isActive() {
            return Instant.now().isBefore(startedAt.plus(WINDOW));
        }
    }
}
