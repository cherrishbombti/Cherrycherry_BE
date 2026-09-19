package com.example.cherry_be.domain.member.service;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 피보호자의 기기 생존 상태 조회·갱신.
 *
 * 기기 단절 감지는 device 도메인의 일이지만 대상과 그 상태는 member 의 것이다.
 * device 쪽에서 MemberRepository 를 직접 부르면 도메인 경계가 리포지토리 레벨에서 뚫리므로,
 * member 도메인이 필요한 만큼만 열어 준다.
 *
 * 웹 화면용 MemberService 와 나누어 둔 이유는 그쪽 메서드가 전부 orgId 를 받는
 * 기관 대시보드 전용이고, 여기는 스케줄러가 부르는 서버 내부 경로라서다.
 */
@Service
@RequiredArgsConstructor
public class MemberLivenessService {

    private final MemberRepository memberRepository;

    /** 끊긴 것으로 판정되고 아직 알리지 않은 피보호자의 id. 발송 여부는 claim 이 다시 판정한다. */
    @Transactional(readOnly = true)
    public List<Long> findNewlyOfflineIds(LocalDateTime deadline) {
        return memberRepository.findNewlyOfflineIds(deadline);
    }

    /**
     * 끊김 알림을 보낼 권리를 확보한다.
     *
     * 이미 다른 인스턴스가 가져갔거나, 그 사이 기기 신호가 다시 들어왔으면 false.
     * 호출자의 트랜잭션에 합류하므로, 이어지는 알림 생성이 실패하면 이 표시도 함께 롤백되어
     * 다음 회차에 다시 시도된다. ("보냈다고 표시만 되고 알림은 없는" 상태가 생기지 않는다)
     */
    @Transactional
    public boolean claimOfflineNotification(Long memberId, LocalDateTime deadline) {
        return memberRepository.claimOfflineNotification(memberId, deadline) == 1;
    }

    @Transactional(readOnly = true)
    public Member getById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
