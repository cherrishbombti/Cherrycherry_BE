package com.example.cherry_be.domain.member.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상태 심각도 비교. 알림을 보낼지 말지가 여기서 갈린다.
 */
class MemberStatusTest {

    @Test
    @DisplayName("나빠지는 방향만 true")
    void worsening() {
        assertThat(MemberStatus.WARNING.isMoreSevereThan(MemberStatus.SAFE)).isTrue();
        assertThat(MemberStatus.DANGER.isMoreSevereThan(MemberStatus.WARNING)).isTrue();
        assertThat(MemberStatus.DANGER.isMoreSevereThan(MemberStatus.SAFE)).isTrue();
    }

    @Test
    @DisplayName("회복은 false — 회복은 알리지 않는다")
    void recovering() {
        assertThat(MemberStatus.SAFE.isMoreSevereThan(MemberStatus.DANGER)).isFalse();
        assertThat(MemberStatus.WARNING.isMoreSevereThan(MemberStatus.DANGER)).isFalse();
    }

    @Test
    @DisplayName("같은 상태는 false")
    void same() {
        assertThat(MemberStatus.DANGER.isMoreSevereThan(MemberStatus.DANGER)).isFalse();
    }

    @Test
    @DisplayName("비교 대상이 null 이면 false (알림을 내지 않는 쪽으로 안전하게)")
    void nullOther() {
        assertThat(MemberStatus.DANGER.isMoreSevereThan(null)).isFalse();
    }

    @Test
    @DisplayName("심각도는 선언 순서(ordinal)가 아니라 level 로 정한다")
    void levelIsExplicit() {
        // 선언 순서를 바꿔도 의미가 따라 바뀌지 않아야 한다.
        assertThat(MemberStatus.SAFE.getLevel()).isZero();
        assertThat(MemberStatus.WARNING.getLevel()).isEqualTo(1);
        assertThat(MemberStatus.DANGER.getLevel()).isEqualTo(2);
    }
}
