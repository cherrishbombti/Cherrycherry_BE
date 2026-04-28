package com.example.cherry_be.global.exception;

import org.springframework.http.ResponseEntity;

public record ErrorResponse(
        int status,
        String code,
        String message
) {
    // ErrorCode를 던져주면, 프론트엔드가 읽기 편한 JSON 형태와 HTTP 상태 코드로 변환해 주는 마법의 메서드입니다.
    public static ResponseEntity<ErrorResponse> toResponseEntity(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(new ErrorResponse(
                        errorCode.getStatus().value(),
                        errorCode.getCode(),
                        errorCode.getMessage()
                ));
    }
}