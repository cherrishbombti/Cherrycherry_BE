package com.example.cherry_be.domain.member.dto;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.entity.MemberStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class MemberSummaryResponse {

    private final Stats stats;
    private final List<MemberInfo> members;

    public MemberSummaryResponse(List<Member> members) {
        this.members = members.stream().map(MemberInfo::new).toList();
        this.stats = new Stats(members);
    }

    // 전체/안전/주의/긴급 카운트
    @Getter
    public static class Stats {
        private final int total;
        private final int safe;
        private final int warning;
        private final int danger;

        public Stats(List<Member> members) {
            this.total   = members.size();
            this.safe    = (int) members.stream().filter(m -> m.getStatus() == MemberStatus.SAFE).count();
            this.warning = (int) members.stream().filter(m -> m.getStatus() == MemberStatus.WARNING).count();
            this.danger  = (int) members.stream().filter(m -> m.getStatus() == MemberStatus.EMERGENCY).count();
        }
    }

    // 개별 피보호자 카드에 필요한 정보
    @Getter
    public static class MemberInfo {
        private final Long id;
        private final String name;
        private final Long age;
        private final String address;
        private final String contact;
        private final MemberStatus status;
        private final Boolean vibrator;
        private final Boolean radar;
        private final Boolean thermal;
        private final LocalDateTime lastUpdated;

        public MemberInfo(Member member) {
            this.id          = member.getId();
            this.name        = member.getName();
            this.age         = member.getAge();
            this.address     = member.getAddress();
            this.contact     = member.getContact();
            this.status      = member.getStatus();
            this.vibrator    = member.getVibrator();
            this.radar       = member.getRadar();
            this.thermal     = member.getThermal();
            this.lastUpdated = member.getLastUpdated();
        }
    }
}
