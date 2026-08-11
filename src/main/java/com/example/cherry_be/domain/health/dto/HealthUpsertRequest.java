package com.example.cherry_be.domain.health.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 건강정보 등록/수정 요청. PUT과 PATCH가 함께 사용한다.
 *
 * PATCH 규칙 (프론트 합의)
 *  - null   : 해당 필드를 수정하지 않음 (기존 값 유지)
 *  - ""     : 값을 비움
 * PUT은 전달된 값으로 전체를 교체하므로 null이면 값이 비워진다.
 *
 * 길이 제한은 컬럼 정의와 맞춘다. 검증이 없으면 DB에서
 * DataIntegrityViolationException이 발생해 500으로 나가므로 DTO 단에서 400으로 처리한다.
 */
@Getter
@NoArgsConstructor
public class HealthUpsertRequest {

    @Size(max = 255, message = "기저질환은 255자를 넘을 수 없습니다.")
    private String disease;

    @Size(max = 255, message = "복용약은 255자를 넘을 수 없습니다.")
    private String medication;

    // memo는 TEXT 컬럼이지만 무제한 입력을 막기 위해 상한을 둔다
    @Size(max = 2000, message = "병력·메모는 2000자를 넘을 수 없습니다.")
    private String memo;
}
