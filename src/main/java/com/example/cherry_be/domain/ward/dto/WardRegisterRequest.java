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
    @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "전화번호 형식이 올바르지 않습니다. (010-XXXX-XXXX)")
    private String phone;

    private String relationship;

    @NotBlank(message = "디바이스 MAC 주소는 필수입니다.")
    @Pattern(
        regexp = "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$",
        message = "MAC 주소 형식이 올바르지 않습니다. (예: AA:BB:CC:DD:EE:FF)"
    )
    private String deviceMac;
}
