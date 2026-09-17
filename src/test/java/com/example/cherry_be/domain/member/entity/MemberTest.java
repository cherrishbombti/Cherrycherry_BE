package com.example.cherry_be.domain.member.entity;

import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTest {

    private Member newMember() {
        return Member.builder().name("김어르신").deviceMac("AA:BB:CC:DD:EE:FF").build();
    }

    private Member withLastSeen(LocalDateTime lastSeen) {
        Member member = newMember();
        ReflectionTestUtils.setField(member, "deviceLastSeen", lastSeen);
        return member;
    }

    @Nested
    @DisplayName("기기 온라인 판정")
    class DeviceOnline {

        @Test
        @DisplayName("한 번도 신호를 받지 않았으면 온라인이 아니다 (연결 대기 중)")
        void neverSeen() {
            assertThat(newMember().getDeviceLastSeen()).isNull();
            assertThat(newMember().isDeviceOnline()).isFalse();
        }

        @Test
        @DisplayName("방금 받았으면 온라인")
        void justSeen() {
            assertThat(withLastSeen(LocalDateTime.now()).isDeviceOnline()).isTrue();
        }

        @Test
        @DisplayName("하트비트 2회 누락(10초)까지는 온라인 — 재시도가 없어 단발 유실은 정상이다")
        void toleratesFewMissedHeartbeats() {
            assertThat(withLastSeen(LocalDateTime.now().minusSeconds(10)).isDeviceOnline()).isTrue();
        }

        @Test
        @DisplayName("25초 무신호면 오프라인")
        void offlineAfterThreshold() {
            assertThat(withLastSeen(LocalDateTime.now().minusSeconds(25)).isDeviceOnline()).isFalse();
        }

        @Test
        @DisplayName("판정 기준 시각은 현재보다 과거다")
        void deadlineIsInThePast() {
            assertThat(Member.offlineDeadline()).isBefore(LocalDateTime.now());
        }
    }

    @Nested
    @DisplayName("기기 수신 시 상태 갱신")
    class UpdateFromDevice {

        @Test
        @DisplayName("수신 시각은 서버 시각으로 채운다")
        void setsServerTime() {
            Member member = newMember();
            LocalDateTime before = LocalDateTime.now();

            member.updateFromDevice(MemberStatus.SAFE, true, true, true, null, null);

            assertThat(member.getDeviceLastSeen()).isAfterOrEqualTo(before);
            assertThat(member.isDeviceOnline()).isTrue();
        }

        @Test
        @DisplayName("배터리·신호는 값이 없으면 기존 값을 유지한다")
        void keepsMetricsWhenAbsent() {
            Member member = newMember();
            member.updateFromDevice(MemberStatus.SAFE, true, true, true, 85, -55);

            member.updateFromDevice(MemberStatus.SAFE, true, true, true, null, null);

            assertThat(member.getBatteryPct()).isEqualTo(85);
            assertThat(member.getRssi()).isEqualTo(-55);
        }

        @Test
        @DisplayName("수신하면 끊김 알림 표시가 풀린다")
        void clearsOfflineNotified() {
            Member member = newMember();
            member.markOfflineNotified();
            assertThat(member).extracting("offlineNotified").isEqualTo(true);

            member.updateFromDevice(MemberStatus.SAFE, true, true, true, null, null);

            assertThat(member).extracting("offlineNotified").isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("소유권과 기관 연동")
    class Ownership {

        private final User guardian = User.builder()
                .id(1L).oauthEmail("g@example.com").name("보호자")
                .oauthProvider(SocialLoginType.GOOGLE).build();

        @Test
        @DisplayName("기관이 직접 등록한 무연고자는 기관이 관리할 수 있다")
        void orgOwnedIsManageable() {
            Member member = Member.builder().name("무연고자").build();
            assertThat(member.isManageable()).isTrue();
        }

        @Test
        @DisplayName("보호자가 등록한 피보호자는 기관이 관리할 수 없다 (조회만)")
        void guardianOwnedIsNotManageable() {
            Member member = Member.builder().name("김어르신").user(guardian).build();
            assertThat(member.isManageable()).isFalse();
        }

        @Test
        @DisplayName("기관을 연동해도 소유자는 여전히 보호자다")
        void linkingOrgKeepsOwnership() {
            Member member = Member.builder().name("김어르신").user(guardian).build();
            Organization org = Organization.builder().orgId("org01").name("복지관").password("x").build();

            member.linkOrganization(org);

            assertThat(member.getOrganization()).isEqualTo(org);
            assertThat(member.getUser()).isEqualTo(guardian);
            assertThat(member.isManageable()).isFalse();
        }

        @Test
        @DisplayName("연동을 해제하면 기관만 떨어진다")
        void unlinking() {
            Member member = Member.builder().name("김어르신").user(guardian).build();
            member.linkOrganization(Organization.builder().orgId("org01").name("복지관").password("x").build());

            member.unlinkOrganization();

            assertThat(member.getOrganization()).isNull();
            assertThat(member.getUser()).isEqualTo(guardian);
        }
    }
}
