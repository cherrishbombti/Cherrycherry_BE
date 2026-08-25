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
    // 휴대폰(01x)·지역번호(0XX)·서울(02) 허용. 2자리 국번은 02만, 나머지는 3자리. 저장 시 숫자만 정규화.
    @Pattern(regexp = "^(?:02|0\\d{2})-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678, 02-123-4567)")
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
