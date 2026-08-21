package com.example.cherry_be.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemberRegisterRequest {

    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    private Long age;          // 나이

    private String address;    // 집 주소

    @NotBlank(message = "연락처는 필수입니다.")
    @Pattern(regexp = "^01[016-9]-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다. (예: 01012345678 또는 010-1234-5678)")
    private String contact;

    @NotBlank(message = "디바이스 ID는 필수입니다.")
    private String deviceMac;  // 라즈베리파이 고유 ID
}
