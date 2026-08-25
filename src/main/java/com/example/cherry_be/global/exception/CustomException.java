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

    /**
     * 예외의 원인 사슬에서 CustomException 을 찾는다. 없으면 null.
     *
     * 프레임워크가 CustomException 을 자기 예외로 감싸 던지는 경로가 있어 필요하다.
     * 예) AttributeConverter 에서 던진 예외를 Hibernate 가
     *     "Error attempting to apply AttributeConverter" 로 포장한다.
     * 이 경우 최상위 타입만 보면 CustomException 을 놓친다.
     */
    public static CustomException unwrap(Throwable e) {
        for (Throwable t = e; t != null && t.getCause() != t; t = t.getCause()) {
            if (t instanceof CustomException custom) {
                return custom;
            }
        }
        return null;
    }

    /** 원인 사슬에 지정한 ErrorCode 의 CustomException 이 있는지. */
    public static boolean has(Throwable e, ErrorCode errorCode) {
        CustomException found = unwrap(e);
        return found != null && found.getErrorCode() == errorCode;
    }
}