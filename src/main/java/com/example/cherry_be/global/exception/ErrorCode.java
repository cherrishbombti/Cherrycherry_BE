package com.example.cherry_be.global.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common (공통 에러)
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 내부에서 에러가 발생했습니다."),

    // Organization (기관 관련 에러 예시)
    ORG_NOT_FOUND(HttpStatus.NOT_FOUND, "O001", "존재하지 않는 기관입니다."),
    ORG_ID_DUPLICATE(HttpStatus.CONFLICT, "O002", "이미 사용 중인 기관 ID입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}