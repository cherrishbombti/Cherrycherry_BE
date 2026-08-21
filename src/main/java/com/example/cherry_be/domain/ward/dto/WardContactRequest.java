package com.example.cherry_be.domain.ward.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WardContactRequest {

    @NotBlank(message = "연락처 이름은 필수입니다.")
    private String name;

    @NotBlank(message = "전화번호는 필수입니다.")
    @Pattern(regexp = "^01[016-9]-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다. (예: 01012345678 또는 010-1234-5678)")
    private String phone;

    private String relationship; // 관계 (선택)
}
