package com.example.cherry_be.global.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "C003", "조회 기간이 올바르지 않습니다. (from이 to보다 늦을 수 없습니다)"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 내부에서 에러가 발생했습니다."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "C004", "요청 형식이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C005", "지원하지 않는 요청 방식입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C006", "요청하신 경로를 찾을 수 없습니다."),
    DATA_CONFLICT(HttpStatus.CONFLICT, "C007", "데이터 제약 조건에 위배되는 요청입니다."),

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
    DEVICE_ALREADY_EXISTS(HttpStatus.CONFLICT, "M003", "이미 등록된 디바이스입니다."),

    // Notification
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "알림을 찾을 수 없습니다."),

    // Device
    DEVICE_NOT_REGISTERED(HttpStatus.NOT_FOUND, "D001", "등록되지 않은 디바이스입니다."),
    INVALID_EVENT_TYPE(HttpStatus.BAD_REQUEST, "D002", "알 수 없는 이벤트 타입입니다."),

    // Social Login
    UNSUPPORTED_SOCIAL_TYPE(HttpStatus.BAD_REQUEST, "S001", "지원하지 않는 소셜 로그인 타입입니다."),
    SOCIAL_LOGIN_FAILED(HttpStatus.BAD_REQUEST, "S002", "소셜 로그인 처리 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
