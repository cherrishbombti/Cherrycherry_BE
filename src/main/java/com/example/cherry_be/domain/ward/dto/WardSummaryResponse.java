package com.example.cherry_be.domain.ward.dto;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.entity.MemberStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WardSummaryResponse {

    private String wardName;          // 피보호자 이름
    private String relationship;      // 관계 (어머니, 아버지 등)
    private String phone;             // 피보호자 전화번호
    private MemberStatus status;      // 현재 상태 (SAFE / WARNING / EMERGENCY)
    private int totalActivityMinutes; // 오늘 총 활동 시간 (분) — 추후 구현, 현재 0
    private int lastActivityMinutes;  // 마지막 활동으로부터 몇 분 전 — 추후 구현, 현재 0
    private LocalDateTime lastUpdatedAt; // 마지막 데이터 수신 시각

    public static WardSummaryResponse from(Member member) {
        return WardSummaryResponse.builder()
                .wardName(member.getName())
                .relationship(member.getRelationship())
                .phone(member.getContact())
                .status(member.getStatus())
                .totalActivityMinutes(0)   // 추후 활동 로그 구현 시 채울 예정
                .lastActivityMinutes(0)    // 추후 활동 로그 구현 시 채울 예정
                .lastUpdatedAt(member.getLastUpdated())
                .build();
    }
}
