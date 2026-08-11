package com.example.cherry_be.domain.health.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 건강정보 전체 등록/수정 요청 (PUT).
 *
 * PUT은 "전체 교체" 의미이므로 세 필드를 모두 보내야 한다.
 *  - 필드 누락(null) : 400. 실수로 기존 병력이 삭제되는 것을 막기 위함
 *  - 빈 문자열("")   : 값을 비움 (명시적 삭제)
 *
 * 부분 수정이 필요하면 PATCH(HealthPatchRequest)를 사용한다.
 */
@Getter
@NoArgsConstructor
public class HealthPutRequest {

    @NotNull(message = "기저질환은 필수입니다. 값을 비우려면 빈 문자열을 보내주세요.")
    @Size(max = 255, message = "기저질환은 255자를 넘을 수 없습니다.")
    private String disease;

    @NotNull(message = "복용약은 필수입니다. 값을 비우려면 빈 문자열을 보내주세요.")
    @Size(max = 255, message = "복용약은 255자를 넘을 수 없습니다.")
    private String medication;

    @NotNull(message = "병력·메모는 필수입니다. 값을 비우려면 빈 문자열을 보내주세요.")
    @Size(max = 2000, message = "병력·메모는 2000자를 넘을 수 없습니다.")
    private String memo;
}
