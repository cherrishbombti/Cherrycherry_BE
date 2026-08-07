package com.example.cherry_be.domain.health.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 건강정보 등록/수정 요청. PUT과 PATCH가 함께 사용한다.
 *
 * PATCH 규칙 (프론트 합의)
 *  - null   : 해당 필드를 수정하지 않음 (기존 값 유지)
 *  - ""     : 값을 비움
 * PUT은 전달된 값으로 전체를 교체하므로 null이면 값이 비워진다.
 */
@Getter
@NoArgsConstructor
public class HealthUpsertRequest {

    private String disease;
    private String medication;
    private String memo;
}
