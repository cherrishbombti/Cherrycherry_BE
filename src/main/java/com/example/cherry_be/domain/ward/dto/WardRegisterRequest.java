package com.example.cherry_be.domain.ward.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WardRegisterRequest {

    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    private String birthDate;

    @NotBlank(message = "주소는 필수입니다.")
    private String address;

    @NotBlank(message = "전화번호는 필수입니다.")
    // 하이픈 유무를 모두 허용하고 저장 시점에 숫자만 남기도록 정규화한다.
    @Pattern(regexp = "^01[016-9]-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다. (예: 01012345678 또는 010-1234-5678)")
    private String phone;

    private String relationship;

    @NotBlank(message = "디바이스 MAC 주소는 필수입니다.")
    @Pattern(
            regexp = "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$",
            message = "MAC 주소 형식이 올바르지 않습니다. (예: AA:BB:CC:DD:EE:FF)"
    )
    private String deviceMac;

    // 보호자 정보 (STEP1)
    private String guardianName;
    private String guardianPhone;

    // 피보호자 기저질환 (선택)
    private String disease;
}
