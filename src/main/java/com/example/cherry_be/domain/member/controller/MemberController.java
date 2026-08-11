package com.example.cherry_be.domain.member.controller;

import com.example.cherry_be.domain.health.dto.HealthResponse;
import com.example.cherry_be.domain.health.dto.HealthUpsertRequest;
import com.example.cherry_be.domain.log.dto.LogPageResponse;
import com.example.cherry_be.domain.member.dto.MemberDetailResponse;
import com.example.cherry_be.domain.member.dto.MemberRegisterRequest;
import com.example.cherry_be.domain.member.dto.MemberSummaryResponse;
import com.example.cherry_be.domain.member.service.MemberService;
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
@RequestMapping("/api/targets")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /**
     * 피보호자 등록
     * [POST] /api/targets
     */
    @PostMapping
    public ResponseEntity<String> registerMember(
            Authentication authentication,
            @RequestBody MemberRegisterRequest request) {
        String orgId = authentication.getName();
        Long savedId = memberService.registerMember(orgId, request);
        return ResponseEntity.ok("피보호자 등록 완료. ID: " + savedId);
    }

    /**
     * 전체 피보호자 조회 + 요약 통계
     * [GET] /api/targets
     */
    @GetMapping
    public ResponseEntity<MemberSummaryResponse> getTargets(Authentication authentication) {
        String orgId = authentication.getName();
        return ResponseEntity.ok(memberService.getTargets(orgId));
    }

    /**
     * 긴급 상태 피보호자만 조회
     * [GET] /api/targets/emergencies
     */
    @GetMapping("/emergencies")
    public ResponseEntity<List<MemberSummaryResponse.MemberInfo>> getEmergencies(
            Authentication authentication) {
        String orgId = authentication.getName();
        return ResponseEntity.ok(memberService.getEmergencies(orgId));
    }

    /**
     * 특정 피보호자 상세 조회
     * [GET] /api/targets/{targetId}
     */
    @GetMapping("/{targetId}")
    public ResponseEntity<MemberDetailResponse> getTargetDetail(
            Authentication authentication,
            @PathVariable Long targetId) {
        String orgId = authentication.getName();
        return ResponseEntity.ok(memberService.getTargetDetail(orgId, targetId));
    }

    /**
     * 피보호자 삭제
     * [DELETE] /api/targets/{targetId}
     */
    @DeleteMapping("/{targetId}")
    public ResponseEntity<String> deleteMember(
            Authentication authentication,
            @PathVariable Long targetId) {
        String orgId = authentication.getName();
        memberService.deleteMember(orgId, targetId);
        return ResponseEntity.ok("피보호자 삭제 완료");
    }

    /**
     * 특정 피보호자 낙상 이력 조회
     * [GET] /api/targets/{targetId}/logs
     * ?page=0&size=20 (기본) 또는 ?from=YYYY-MM-DD&to=YYYY-MM-DD
     */
    @GetMapping("/{targetId}/logs")
    public ResponseEntity<LogPageResponse> getTargetLogs(
            Authentication authentication,
            @PathVariable Long targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                memberService.getLogs(authentication.getName(), targetId, from, to, pageable));
    }


    /**
     * 피보호자 건강 정보 조회
     * [GET] /api/targets/{targetId}/health
     */
    @GetMapping("/{targetId}/health")
    public ResponseEntity<HealthResponse> getTargetHealth(
            Authentication authentication,
            @PathVariable Long targetId) {
        return ResponseEntity.ok(
                memberService.getHealth(authentication.getName(), targetId));
    }


    /**
     * 피보호자 건강 정보 전체 등록/수정
     * [PUT] /api/targets/{targetId}/health
     */
    @PutMapping("/{targetId}/health")
    public ResponseEntity<HealthResponse> putTargetHealth(
            Authentication authentication,
            @PathVariable Long targetId,
            @Valid @RequestBody HealthUpsertRequest request) {
        return ResponseEntity.ok(
                memberService.putHealth(authentication.getName(), targetId, request));
    }

    /**
     * 피보호자 건강 정보 부분 수정
     * [PATCH] /api/targets/{targetId}/health
     */
    @PatchMapping("/{targetId}/health")
    public ResponseEntity<HealthResponse> patchTargetHealth(
            Authentication authentication,
            @PathVariable Long targetId,
            @Valid @RequestBody HealthUpsertRequest request) {
        return ResponseEntity.ok(
                memberService.patchHealth(authentication.getName(), targetId, request));
    }

}
