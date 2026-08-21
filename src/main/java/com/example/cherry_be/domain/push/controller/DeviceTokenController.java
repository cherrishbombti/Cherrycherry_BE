package com.example.cherry_be.domain.push.controller;

import com.example.cherry_be.domain.push.dto.DeviceTokenRequest;
import com.example.cherry_be.domain.push.service.DeviceTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 푸시 토큰 등록/삭제.
 * 라즈베리파이 수신 경로(/api/device/data)와 혼동하지 않도록 /api/push 하위에 둔다.
 */
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    /**
     * [POST] /api/push/token — 토큰 등록 (보호자·기관 공통)
     * 앱 실행·로그인 시 호출한다. 같은 토큰을 다시 보내면 소유자만 갱신된다.
     */
    @PostMapping("/token")
    public ResponseEntity<Void> register(
            Authentication authentication,
            @Valid @RequestBody DeviceTokenRequest request) {
        deviceTokenService.register(authentication, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * [DELETE] /api/push/token — 토큰 삭제 (로그아웃 시)
     */
    @DeleteMapping("/token")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @RequestParam String token) {
        deviceTokenService.delete(authentication, token);
        return ResponseEntity.noContent().build();
    }
}
