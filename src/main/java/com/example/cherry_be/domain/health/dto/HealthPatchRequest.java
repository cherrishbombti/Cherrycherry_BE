package com.example.cherry_be.domain.health.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 건강정보 부분 수정 요청 (PATCH).
 *
 * 전달된 필드만 수정한다.
 *  - null          : 해당 필드를 수정하지 않음 (기존 값 유지)
 *  - 빈 문자열("") : 값을 비움
 *
 * 전체 교체가 필요하면 PUT(HealthPutRequest)을 사용한다.
 */
@Getter
@NoArgsConstructor
public class HealthPatchRequest {

    @Size(max = 255, message = "기저질환은 255자를 넘을 수 없습니다.")
    private String disease;

    @Size(max = 255, message = "복용약은 255자를 넘을 수 없습니다.")
    private String medication;

    @Size(max = 2000, message = "병력·메모는 2000자를 넘을 수 없습니다.")
    private String memo;
}
