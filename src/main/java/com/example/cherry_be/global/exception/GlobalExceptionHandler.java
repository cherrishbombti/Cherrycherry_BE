package com.example.cherry_be.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. 우리가 직접 발생시킨 CustomException 처리
    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        log.warn("CustomException : {}", e.getErrorCode().getMessage());
        return ErrorResponse.toResponseEntity(e.getErrorCode());
    }

    // 2. 프론트엔드에서 보낸 DTO 검증(@Valid) 실패 시 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        // 어떤 필드가 왜 틀렸는지 그대로 전달한다.
        // 기존처럼 "잘못된 입력값입니다."만 내려주면 프론트가 원인을 안내할 수 없다.
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        if (detail.isBlank()) {
            detail = ErrorCode.INVALID_INPUT_VALUE.getMessage();
        }
        log.warn("ValidationException : {}", detail);
        return ErrorResponse.toResponseEntity(ErrorCode.INVALID_INPUT_VALUE, detail);
    }

    // 3. 서비스단 IllegalArgumentException → 400 (미처리 시 500으로 나가는 것 방지)
    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("IllegalArgumentException : {}", e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.INVALID_INPUT_VALUE);
    }

    // 4. 그 외 짐작하지 못한 모든 예외(500 에러) 처리 (최후의 방어선)
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleAllException(Exception e) {
        log.error("Unhandled Exception : {}", e.getMessage(), e);
        return ErrorResponse.toResponseEntity(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}