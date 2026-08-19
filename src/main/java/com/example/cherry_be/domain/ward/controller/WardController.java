package com.example.cherry_be.domain.ward.controller;

import com.example.cherry_be.domain.health.dto.HealthResponse;
import com.example.cherry_be.domain.health.dto.HealthPatchRequest;
import com.example.cherry_be.domain.health.dto.HealthPutRequest;
import com.example.cherry_be.domain.log.dto.LogPageResponse;
import com.example.cherry_be.domain.notification.dto.NotificationPageResponse;
import com.example.cherry_be.domain.ward.dto.*;
import com.example.cherry_be.domain.ward.service.WardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/wards")
@RequiredArgsConstructor
public class WardController {

    private final WardService wardService;

    /**
     * [POST] /api/wards/me — 피보호자 최초 등록
     * @Valid 로 DTO 단에서 MAC 주소, 전화번호 형식 사전 검증
     */
    @PostMapping("/me")
    public ResponseEntity<String> registerWard(
            Authentication authentication,
            @Valid @RequestBody WardRegisterRequest request) {
        String oauthEmail = authentication.getName();
        Long wardId = wardService.registerWard(oauthEmail, request);
        return ResponseEntity.ok("피보호자 등록 완료. ID: " + wardId);
    }

    /**
     * [GET] /api/wards/me/summary — 홈 화면 요약 정보
     */
    @GetMapping("/me/summary")
    public ResponseEntity<WardSummaryResponse> getSummary(Authentication authentication) {
        return ResponseEntity.ok(wardService.getSummary(authentication.getName()));
    }

    /**
     * [GET] /api/wards/me/sensors — 센서 상태 조회
     */
    @GetMapping("/me/sensors")
    public ResponseEntity<WardSensorResponse> getSensors(Authentication authentication) {
        return ResponseEntity.ok(wardService.getSensors(authentication.getName()));
    }

    /**
     * [GET] /api/wards/me/contacts — 비상연락망 목록 조회
     */
    @GetMapping("/me/contacts")
    public ResponseEntity<List<WardContactResponse>> getContacts(Authentication authentication) {
        return ResponseEntity.ok(wardService.getContacts(authentication.getName()));
    }

    /**
     * [POST] /api/wards/me/contacts — 비상연락망 등록
     */
    @PostMapping("/me/contacts")
    public ResponseEntity<WardContactResponse> addContact(
            Authentication authentication,
            @RequestBody WardContactRequest request) {
        return ResponseEntity.ok(wardService.addContact(authentication.getName(), request));
    }

    /** [PUT] /api/wards/me/contacts/{contactId} — 비상연락망 수정 */
    @PutMapping("/me/contacts/{contactId}")
    public ResponseEntity<WardContactResponse> updateContact(
            Authentication authentication,
            @PathVariable Long contactId,
            @RequestBody WardContactRequest request) {
        return ResponseEntity.ok(
                wardService.updateContact(authentication.getName(), contactId, request));
    }

    /** [DELETE] /api/wards/me/contacts/{contactId} — 비상연락망 삭제 */
    @DeleteMapping("/me/contacts/{contactId}")
    public ResponseEntity<Void> deleteContact(
            Authentication authentication,
            @PathVariable Long contactId) {
        wardService.deleteContact(authentication.getName(), contactId);
        return ResponseEntity.noContent().build();
    }

    /**
     * [GET] /api/wards/me/logs — 낙상 이력 조회
     * ?page=0&size=20 (기본) 또는 ?from=YYYY-MM-DD&to=YYYY-MM-DD
     */
    @GetMapping("/me/logs")
    public ResponseEntity<LogPageResponse> getLogs(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                wardService.getLogs(authentication.getName(), from, to, pageable));
    }


    /**
     * [POST] /api/wards/me/emergency-log — 119 신고 버튼 클릭 이력 저장
     * 요청 본문 없음. 프론트는 응답을 기다리지 말고 즉시 통화를 실행할 것.
     */
    @PostMapping("/me/emergency-log")
    public ResponseEntity<EmergencyLogResponse> addEmergencyLog(Authentication authentication) {
        return ResponseEntity.ok(wardService.addEmergencyLog(authentication.getName()));
    }


    // ── 건강정보 (#26) ──────────────────────────────

    /**
     * [GET] /api/wards/me/health — 건강정보 조회
     * 아직 등록 전이면 빈 값이 담긴 200을 반환한다.
     */
    @GetMapping("/me/health")
    public ResponseEntity<HealthResponse> getHealth(Authentication authentication) {
        return ResponseEntity.ok(wardService.getHealth(authentication.getName()));
    }

    /**
     * [PUT] /api/wards/me/health — 전체 등록/수정 (upsert)
     */
    @PutMapping("/me/health")
    public ResponseEntity<HealthResponse> putHealth(
            Authentication authentication,
            @Valid @RequestBody HealthPutRequest request) {
        return ResponseEntity.ok(wardService.putHealth(authentication.getName(), request));
    }

    /**
     * [PATCH] /api/wards/me/health — 부분 수정
     * null인 필드는 수정하지 않고, 값을 비우려면 빈 문자열을 보낸다.
     */
    @PatchMapping("/me/health")
    public ResponseEntity<HealthResponse> patchHealth(
            Authentication authentication,
            @Valid @RequestBody HealthPatchRequest request) {
        return ResponseEntity.ok(wardService.patchHealth(authentication.getName(), request));
    }


    // ── 알림함 (#25) ────────────────────────────────

    /**
     * [GET] /api/wards/me/notifications — 알림 목록 (최신순)
     */
    @GetMapping("/me/notifications")
    public ResponseEntity<NotificationPageResponse> getNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                wardService.getNotifications(authentication.getName(), pageable));
    }

    /**
     * [PATCH] /api/wards/me/notifications/{notificationId}/read — 단건 읽음 처리
     */
    @PatchMapping("/me/notifications/{notificationId}/read")
    public ResponseEntity<Void> readNotification(
            Authentication authentication,
            @PathVariable Long notificationId) {
        wardService.readNotification(authentication.getName(), notificationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * [PATCH] /api/wards/me/notifications/read-all — 전체 읽음 처리
     */
    @PatchMapping("/me/notifications/read-all")
    public ResponseEntity<Void> readAllNotifications(Authentication authentication) {
        wardService.readAllNotifications(authentication.getName());
        return ResponseEntity.noContent().build();
    }

}
