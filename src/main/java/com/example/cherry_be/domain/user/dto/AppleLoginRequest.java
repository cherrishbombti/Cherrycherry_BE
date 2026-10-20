package com.example.cherry_be.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 앱(iOS) Sign in with Apple 결과.
 * name 은 Apple 이 "최초 로그인 때만" 주므로 없을 수 있다.
 */
public record AppleLoginRequest(
        @NotBlank String identityToken,
        String authorizationCode,
        String name
) {}
