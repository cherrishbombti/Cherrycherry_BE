package com.example.cherry_be.domain.ward.controller;

import com.example.cherry_be.domain.log.dto.LogPageResponse;
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

}
