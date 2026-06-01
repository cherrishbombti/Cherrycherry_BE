package com.example.cherry_be.domain.member.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemberRegisterRequest {
    private String name;       // 피보호자 이름
    private Long age;          // 나이
    private String address;    // 집 주소
    private String contact;    // 연락처
    private String deviceMac;  // 라즈베리파이 고유 ID
}
