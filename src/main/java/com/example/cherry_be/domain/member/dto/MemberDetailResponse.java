package com.example.cherry_be.domain.member.dto;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.ward.dto.WardContactResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.cherry_be.domain.member.entity.MemberStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class MemberDetailResponse {

    private final Long id;
    private final String name;
    private final Long age;
    private final String address;
    @JsonProperty("phone")
    private final String contact;
    private final String deviceMac;
    private final MemberStatus status;
    private final Boolean vibrator;
    private final Boolean radar;
    private final Boolean thermal;
    private final Boolean deviceOnline;          // 계산값 (저장하지 않음)
    private final LocalDateTime deviceLastSeen;  // 서버 수신 시각, 미수신 시 null

    // 기관이 이 피보호자를 삭제·수정할 수 있는지 (계산값)
    // false = 보호자가 등록한 피보호자라 기관은 조회만 가능
    @JsonProperty("manageable")
    private final boolean manageable;

    // 비상연락망. 보호자가 등록한 피보호자만 값이 있고,
    // 기관이 등록한 피보호자(무연고자)는 보통 빈 배열이다.
    private final List<WardContactResponse> contacts;

    public MemberDetailResponse(Member member, List<WardContactResponse> contacts) {
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
        this.deviceOnline   = member.isDeviceOnline();
        this.deviceLastSeen = member.getDeviceLastSeen();
        this.manageable     = member.isManageable();
        this.contacts       = contacts;
    }
}
