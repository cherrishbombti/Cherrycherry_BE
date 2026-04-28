package com.example.cherry_be.global.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        // 부모 클래스(RuntimeException)에 메시지를 넘겨주어 서버 콘솔 로그에도 찍히게 합니다.
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}