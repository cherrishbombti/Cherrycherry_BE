package com.example.cherry_be.domain.member.dto;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.entity.MemberStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class MemberDetailResponse {

    private final Long id;
    private final String name;
    private final Long age;
    private final String address;
    private final String contact;
    private final String deviceMac;
    private final MemberStatus status;
    private final Boolean vibrator;
    private final Boolean radar;
    private final Boolean thermal;
    private final LocalDateTime lastUpdated;

    public MemberDetailResponse(Member member) {
        this.id          = member.getId();
        this.name        = member.getName();
        this.age         = member.getAge();
        this.address     = member.getAddress();
        this.contact     = member.getContact();
        this.deviceMac   = member.getDeviceMac();
        this.status      = member.getStatus();
        this.vibrator    = member.getVibrator();
        this.radar       = member.getRadar();
        this.thermal     = member.getThermal();
        this.lastUpdated = member.getLastUpdated();
    }
}
