package com.example.cherry_be.domain.ward.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보호자가 입력하는 기관번호. 기관이 안내한 번호를 앱에서 그대로 입력한다.
 */
@Getter
@NoArgsConstructor
public class WardOrganizationRequest {

    @NotNull(message = "기관번호를 입력해주세요.")
    private Long orgCode;
}
