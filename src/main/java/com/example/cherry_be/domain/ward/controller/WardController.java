package com.example.cherry_be.domain.ward.controller;

import com.example.cherry_be.domain.health.dto.HealthResponse;
import com.example.cherry_be.domain.health.dto.HealthPatchRequest;
import com.example.cherry_be.domain.health.dto.HealthPutRequest;
import com.example.cherry_be.domain.log.dto.LogPageResponse;
import com.example.cherry_be.domain.notification.dto.NotificationPageResponse;
import com.example.cherry_be.domain.ward.dto.*;
import com.example.cherry_be.domain.ward.service.WardContactService;
import com.example.cherry_be.domain.ward.service.WardHealthService;
import com.example.cherry_be.domain.ward.service.WardLogService;
import com.example.cherry_be.domain.ward.service.WardNotificationService;
import com.example.cherry_be.domain.ward.service.WardOrgLinkService;
import com.example.cherry_be.domain.ward.service.WardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import com.example.cherry_be.global.auth.GuardianEmail;

@RestController
@RequestMapping("/api/wards")
@RequiredArgsConstructor
public class WardController {

    // 보호자 API 는 관심사별로 서비스가 나뉘어 있다. 경로와 서비스가 1:1 로 대응한다.
    private final WardService wardService;                          // 등록·요약·센서
    private final WardOrgLinkService wardOrgLinkService;            // 기관 연동
    private final WardContactService wardContactService;            // 비상연락망
    private final WardLogService wardLogService;                    // 사건 이력
    private final WardHealthService wardHealthService;              // 건강정보
    private final WardNotificationService wardNotificationService;  // 알림함

    /**
     * [POST] /api/wards/me — 피보호자 최초 등록
     * @Valid 로 DTO 단에서 MAC 주소, 전화번호 형식 사전 검증
     */
    @PostMapping("/me")
    public ResponseEntity<String> registerWard(
            GuardianEmail guardian,
            @Valid @RequestBody WardRegisterRequest request) {
        Long wardId = wardService.registerWard(guardian, request);
        return ResponseEntity.ok("피보호자 등록 완료. ID: " + wardId);
    }

    /**
     * [GET] /api/wards/me/summary — 홈 화면 요약 정보
     */
    @GetMapping("/me/summary")
    public ResponseEntity<WardSummaryResponse> getSummary(GuardianEmail guardian) {
        return ResponseEntity.ok(wardService.getSummary(guardian));
    }

    /**
     * [GET] /api/wards/me/sensors — 센서 상태 조회
     */
    @GetMapping("/me/sensors")
    public ResponseEntity<WardSensorResponse> getSensors(GuardianEmail guardian) {
        return ResponseEntity.ok(wardService.getSensors(guardian));
    }

    // ── 기관 연동 (#45) ─────────────────────────────

    /**
     * [GET] /api/wards/me/organization — 현재 기관 연동 상태
     * 연동 전이면 { "linked": false } 만 내려간다.
     */
    @GetMapping("/me/organization")
    public ResponseEntity<WardOrganizationResponse> getOrganization(GuardianEmail guardian) {
        return ResponseEntity.ok(wardOrgLinkService.getOrganization(guardian));
    }

    /**
     * [PATCH] /api/wards/me/organization — 기관번호로 연동
     * 이미 연동돼 있으면 새 기관으로 교체된다.
     */
    @PatchMapping("/me/organization")
    public ResponseEntity<WardOrganizationResponse> linkOrganization(
            GuardianEmail guardian,
            @Valid @RequestBody WardOrganizationRequest request) {
        return ResponseEntity.ok(
                wardOrgLinkService.linkOrganization(guardian, request));
    }

    /**
     * [DELETE] /api/wards/me/organization — 기관 연동 해제
     */
    @DeleteMapping("/me/organization")
    public ResponseEntity<Void> unlinkOrganization(GuardianEmail guardian) {
        wardOrgLinkService.unlinkOrganization(guardian);
        return ResponseEntity.noContent().build();
    }

    /**
     * [GET] /api/wards/me/contacts — 비상연락망 목록 조회
     */
    @GetMapping("/me/contacts")
    public ResponseEntity<List<WardContactResponse>> getContacts(GuardianEmail guardian) {
        return ResponseEntity.ok(wardContactService.getContacts(guardian));
    }

    /**
     * [POST] /api/wards/me/contacts — 비상연락망 등록
     */
    @PostMapping("/me/contacts")
    public ResponseEntity<WardContactResponse> addContact(
            GuardianEmail guardian,
            @Valid @RequestBody WardContactRequest request) {
        return ResponseEntity.ok(wardContactService.addContact(guardian, request));
    }

    /** [PUT] /api/wards/me/contacts/{contactId} — 비상연락망 수정 */
    @PutMapping("/me/contacts/{contactId}")
    public ResponseEntity<WardContactResponse> updateContact(
            GuardianEmail guardian,
            @PathVariable Long contactId,
            @Valid @RequestBody WardContactRequest request) {
        return ResponseEntity.ok(
                wardContactService.updateContact(guardian, contactId, request));
    }

    /** [DELETE] /api/wards/me/contacts/{contactId} — 비상연락망 삭제 */
    @DeleteMapping("/me/contacts/{contactId}")
    public ResponseEntity<Void> deleteContact(
            GuardianEmail guardian,
            @PathVariable Long contactId) {
        wardContactService.deleteContact(guardian, contactId);
        return ResponseEntity.noContent().build();
    }

    /**
     * [GET] /api/wards/me/logs — 낙상 이력 조회
     * ?page=0&size=20 (기본) 또는 ?from=YYYY-MM-DD&to=YYYY-MM-DD
     */
    @GetMapping("/me/logs")
    public ResponseEntity<LogPageResponse> getLogs(
            GuardianEmail guardian,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                wardLogService.getLogs(guardian, from, to, pageable));
    }


    /**
     * [POST] /api/wards/me/emergency-log — 119 신고 버튼 클릭 이력 저장
     * 요청 본문 없음. 프론트는 응답을 기다리지 말고 즉시 통화를 실행할 것.
     */
    @PostMapping("/me/emergency-log")
    public ResponseEntity<EmergencyLogResponse> addEmergencyLog(GuardianEmail guardian) {
        return ResponseEntity.ok(wardLogService.addEmergencyLog(guardian));
    }


    // ── 건강정보 (#26) ──────────────────────────────

    /**
     * [GET] /api/wards/me/health — 건강정보 조회
     * 아직 등록 전이면 빈 값이 담긴 200을 반환한다.
     */
    @GetMapping("/me/health")
    public ResponseEntity<HealthResponse> getHealth(GuardianEmail guardian) {
        return ResponseEntity.ok(wardHealthService.getHealth(guardian));
    }

    /**
     * [PUT] /api/wards/me/health — 전체 등록/수정 (upsert)
     */
    @PutMapping("/me/health")
    public ResponseEntity<HealthResponse> putHealth(
            GuardianEmail guardian,
            @Valid @RequestBody HealthPutRequest request) {
        return ResponseEntity.ok(wardHealthService.putHealth(guardian, request));
    }

    /**
     * [PATCH] /api/wards/me/health — 부분 수정
     * null인 필드는 수정하지 않고, 값을 비우려면 빈 문자열을 보낸다.
     */
    @PatchMapping("/me/health")
    public ResponseEntity<HealthResponse> patchHealth(
            GuardianEmail guardian,
            @Valid @RequestBody HealthPatchRequest request) {
        return ResponseEntity.ok(wardHealthService.patchHealth(guardian, request));
    }

    /**
     * [DELETE] /api/wards/me/health — 건강정보 삭제
     * 저장값을 읽지 못하게 된 경우의 복구 경로이기도 하다.
     */
    @DeleteMapping("/me/health")
    public ResponseEntity<Void> deleteHealth(GuardianEmail guardian) {
        wardHealthService.deleteHealth(guardian);
        return ResponseEntity.noContent().build();
    }


    // ── 알림함 (#25) ────────────────────────────────

    /**
     * [GET] /api/wards/me/notifications — 알림 목록 (최신순)
     */
    @GetMapping("/me/notifications")
    public ResponseEntity<NotificationPageResponse> getNotifications(
            GuardianEmail guardian,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                wardNotificationService.getNotifications(guardian, pageable));
    }

    /**
     * [PATCH] /api/wards/me/notifications/{notificationId}/read — 단건 읽음 처리
     */
    @PatchMapping("/me/notifications/{notificationId}/read")
    public ResponseEntity<Void> readNotification(
            GuardianEmail guardian,
            @PathVariable Long notificationId) {
        wardNotificationService.readNotification(guardian, notificationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * [PATCH] /api/wards/me/notifications/read-all — 전체 읽음 처리
     */
    @PatchMapping("/me/notifications/read-all")
    public ResponseEntity<Void> readAllNotifications(GuardianEmail guardian) {
        wardNotificationService.readAllNotifications(guardian);
        return ResponseEntity.noContent().build();
    }

    /**
     * [GET] /api/wards/me/notifications/unread-count — 미읽음 알림 개수 (배지용)
     */
    @GetMapping("/me/notifications/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(GuardianEmail guardian) {
        return ResponseEntity.ok(wardNotificationService.getUnreadCount(guardian));
    }

}
