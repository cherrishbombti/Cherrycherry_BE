package com.example.cherry_be.domain.member.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MemberStatus {

    SAFE(0),     // 안전
    WARNING(1),  // 주의
    DANGER(2);   // 긴급

    /**
     * 심각도. 값이 클수록 위험하다.
     * ordinal() 을 쓰면 enum 선언 순서가 바뀔 때 의미가 함께 바뀌므로 명시적으로 둔다.
     */
    private final int level;

    /**
     * 상태가 나빠지는 방향인지 판단한다.
     * 예) SAFE -> WARNING(true), WARNING -> DANGER(true), DANGER -> WARNING(false)
     */
    public boolean isMoreSevereThan(MemberStatus other) {
        return other != null && this.level > other.level;
    }
}
