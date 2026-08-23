package com.example.cherry_be.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;

/**
 * 전역 예외 처리.
 *
 * 원칙
 *  - 클라이언트 잘못은 4xx, 서버 잘못만 5xx 로 내려보낸다.
 *  - 예상 가능한 예외를 개별 핸들러로 분기하고, 마지막 Exception 핸들러는
 *    "미처리 예외"만 걸러내는 최후의 방어선으로 남긴다.
 *  - DB 오류 원문·스택트레이스 등 내부 정보는 응답에 노출하지 않고 서버 로그에만 남긴다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 1. 직접 발생시킨 예외 ────────────────────────────────

    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        log.warn("CustomException : {}", e.getErrorCode().getMessage());
        return ErrorResponse.toResponseEntity(e.getErrorCode());
    }

    // ── 2. 요청 값 검증 실패 (400) ──────────────────────────

    /**
     * @Valid 로 검증한 요청 바디가 규칙에 맞지 않는 경우.
     * 어떤 필드가 왜 틀렸는지 전달해야 프론트가 원인을 안내할 수 있다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        if (detail.isBlank()) {
            detail = ErrorCode.INVALID_INPUT_VALUE.getMessage();
        }
        log.warn("ValidationException : {}", detail);
        return ErrorResponse.toResponseEntity(ErrorCode.INVALID_INPUT_VALUE, detail);
    }

    /**
     * @Validated 를 붙인 파라미터·경로변수 검증 실패.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    protected ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        log.warn("ConstraintViolationException : {}", detail);
        return ErrorResponse.toResponseEntity(ErrorCode.INVALID_INPUT_VALUE, detail);
    }

    // ── 3. 요청 형식 자체가 잘못된 경우 (400) ────────────────

    /**
     * JSON 문법 오류 등으로 바디를 읽지 못한 경우.
     * 파싱 실패 원문에는 패키지·필드 구조가 드러나므로 응답에는 노출하지 않는다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    protected ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadableException : {}", e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.MALFORMED_REQUEST);
    }

    /**
     * 경로변수·쿼리파라미터 타입 불일치.
     * 예) /api/targets/abc/logs — Long 자리에 문자열
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("MethodArgumentTypeMismatchException : {} = {}", e.getName(), e.getValue());
        return ErrorResponse.toResponseEntity(
                ErrorCode.INVALID_INPUT_VALUE, e.getName() + " 값이 올바르지 않습니다.");
    }

    /**
     * 필수 쿼리파라미터 누락.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    protected ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("MissingServletRequestParameterException : {}", e.getParameterName());
        return ErrorResponse.toResponseEntity(
                ErrorCode.INVALID_INPUT_VALUE, e.getParameterName() + " 파라미터가 필요합니다.");
    }

    /**
     * 날짜 문자열 파싱 실패.
     * 예) 피보호자 등록 시 birthDate 형식 오류 — 기존에는 500 으로 나갔다.
     */
    @ExceptionHandler(DateTimeParseException.class)
    protected ResponseEntity<ErrorResponse> handleDateTimeParse(DateTimeParseException e) {
        log.warn("DateTimeParseException : {}", e.getParsedString());
        return ErrorResponse.toResponseEntity(
                ErrorCode.INVALID_INPUT_VALUE, "날짜 형식이 올바르지 않습니다. (YYYY-MM-DD)");
    }

    /**
     * 서비스단에서 던지는 IllegalArgumentException 안전망.
     * 의미가 분명한 곳은 CustomException 을 쓰고, 여기까지 오면 미처리로 간주한다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("IllegalArgumentException : {}", e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.INVALID_INPUT_VALUE);
    }

    // ── 4. 라우팅 (404 / 405) ───────────────────────────────

    @ExceptionHandler(NoResourceFoundException.class)
    protected ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        log.warn("NoResourceFoundException : {}", e.getResourcePath());
        return ErrorResponse.toResponseEntity(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("HttpRequestMethodNotSupportedException : {}", e.getMethod());
        return ErrorResponse.toResponseEntity(ErrorCode.METHOD_NOT_ALLOWED);
    }

    // ── 5. DB 제약 위반 (409) ───────────────────────────────

    /**
     * UNIQUE·NOT NULL·길이 초과 등 DB 제약 위반.
     * 사전 검증으로 걸러내는 것이 원칙이므로 여기까지 온 것은 검증 누락으로 보고 error 로 남긴다.
     * DB 오류 원문에는 테이블·컬럼명이 드러나므로 응답에는 노출하지 않는다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    protected ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        log.error("DataIntegrityViolationException : {}", e.getMostSpecificCause().getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.DATA_CONFLICT);
    }

    // ── 6. 최후의 방어선 (500) ──────────────────────────────

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleAllException(Exception e) {
        // 프레임워크가 CustomException 을 감싸 던지는 경로가 있다.
        // (예: AttributeConverter 에서 던진 예외를 Hibernate 가 자체 예외로 포장)
        // 원인 사슬에 CustomException 이 있으면 그 코드를 그대로 쓴다.
        CustomException wrapped = findCustomException(e);
        if (wrapped != null) {
            log.error("Wrapped CustomException : {}", wrapped.getErrorCode().getMessage(), e);
            return ErrorResponse.toResponseEntity(wrapped.getErrorCode());
        }

        log.error("Unhandled Exception : {}", e.getMessage(), e);
        return ErrorResponse.toResponseEntity(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private CustomException findCustomException(Throwable e) {
        for (Throwable t = e; t != null && t.getCause() != t; t = t.getCause()) {
            if (t instanceof CustomException custom) {
                return custom;
            }
        }
        return null;
    }
}
