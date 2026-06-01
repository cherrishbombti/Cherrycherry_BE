package com.example.cherry_be.global.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 내부에서 에러가 발생했습니다."),

    // Organization
    ORG_NOT_FOUND(HttpStatus.NOT_FOUND, "O001", "존재하지 않는 기관입니다."),
    ORG_ID_DUPLICATE(HttpStatus.CONFLICT, "O002", "이미 사용 중인 기관 ID입니다."),

    // Auth
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "A001", "아이디 또는 비밀번호가 일치하지 않습니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "존재하지 않는 사용자입니다."),

    // Member / Ward
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "연결된 피보호자를 찾을 수 없습니다."),
    WARD_ALREADY_EXISTS(HttpStatus.CONFLICT, "M002", "이미 등록된 피보호자가 있습니다."),
    DEVICE_ALREADY_EXISTS(HttpStatus.CONFLICT, "M003", "이미 등록된 디바이스입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
