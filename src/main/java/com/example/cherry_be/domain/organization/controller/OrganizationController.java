package com.example.cherry_be.domain.organization.controller;

import com.example.cherry_be.domain.organization.dto.LoginRequest;
import com.example.cherry_be.domain.notification.dto.NotificationPageResponse;
import com.example.cherry_be.domain.organization.dto.OrgMeResponse;
import com.example.cherry_be.domain.organization.dto.SignUpRequest;
import com.example.cherry_be.domain.organization.service.OrganizationService;
import com.example.cherry_be.domain.organization.service.OrganizationService.LoginTokens;
import com.example.cherry_be.global.auth.RefreshCookie;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController // 이 클래스가 REST API 안내데스크 역할을 한다고 선언
@RequestMapping("/api/org") // 이 컨트롤러의 기본 주소 설정
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final RefreshCookie refreshCookie;

    /**
     * 기관 회원가입 API
     * [POST] /api/org/signup
     */
    @PostMapping("/signup")
    public ResponseEntity<String> signUp(@RequestBody SignUpRequest request) {
        // 1. 리액트에서 보낸 DTO 데이터(상자)를 열어서 Service 부서로 넘겨줌
        Long savedOrgId = organizationService.signUp(
                request.getOrgId(),
                request.getPassword(),
                request.getName()
        );

        // 2. Service 부서가 일을 성공적으로 마치면 리액트에게 성공 메시지와 상태 코드 200(OK)을 반환
        return ResponseEntity.ok("회원가입이 완료되었습니다. 고유 ID: " + savedOrgId);
    }

    /**
     * 기관 로그인 API
     * [POST] /api/org/login
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {

        // 1. Service 부서로 아이디와 비밀번호를 넘겨서 검증받고, 성공하면 토큰을 받아옵니다.
        LoginTokens tokens = organizationService.login(request.getOrgId(), request.getPassword());

        // 2. 리액트(프론트엔드)가 쉽게 꺼내 쓸 수 있도록 {"token": "eyJhbGci..."} 형태의 JSON으로 포장합니다.
        Map<String, String> response = new HashMap<>();
        response.put("token", tokens.accessToken());

        // 3. 기관 콘솔은 웹 전용이므로 리프레시 토큰은 httpOnly 쿠키로만 내려보냅니다(#52).
        //    본문에 담으면 JS가 읽을 수 있어 쿠키를 쓰는 의미가 없어집니다.
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.create(tokens.refreshToken()).toString())
                .body(response);
    }


    /**
     * 로그인한 기관 본인 정보 조회 API
     * [GET] /api/org/me
     */
    @GetMapping("/me")
    public ResponseEntity<OrgMeResponse> getMyInfo(Authentication authentication) {
        String orgId = authentication.getName();
        return ResponseEntity.ok(organizationService.getMyInfo(orgId));
    }


    // ── 알림함 (#25) ────────────────────────────────

    /**
     * [GET] /api/org/notifications — 기관 알림 목록 (최신순)
     */
    @GetMapping("/notifications")
    public ResponseEntity<NotificationPageResponse> getNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(organizationService.getNotifications(
                authentication.getName(), PageRequest.of(page, size)));
    }

    /**
     * [PATCH] /api/org/notifications/{notificationId}/read — 단건 읽음 처리
     */
    @PatchMapping("/notifications/{notificationId}/read")
    public ResponseEntity<Void> readNotification(
            Authentication authentication,
            @PathVariable Long notificationId) {
        organizationService.readNotification(authentication.getName(), notificationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * [PATCH] /api/org/notifications/read-all — 전체 읽음 처리
     */
    @PatchMapping("/notifications/read-all")
    public ResponseEntity<Void> readAllNotifications(Authentication authentication) {
        organizationService.readAllNotifications(authentication.getName());
        return ResponseEntity.noContent().build();
    }

}
