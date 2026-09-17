package com.example.cherry_be.domain.member.controller;

import com.example.cherry_be.domain.health.dto.HealthResponse;
import com.example.cherry_be.domain.health.dto.HealthPatchRequest;
import com.example.cherry_be.domain.health.dto.HealthPutRequest;
import com.example.cherry_be.domain.log.dto.LogPageResponse;
import com.example.cherry_be.domain.member.dto.MemberDetailResponse;
import com.example.cherry_be.domain.member.dto.MemberRegisterRequest;
import com.example.cherry_be.domain.member.dto.MemberSummaryResponse;
import com.example.cherry_be.domain.member.service.MemberHealthAccessService;
import com.example.cherry_be.domain.member.service.MemberLogService;
import com.example.cherry_be.domain.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import com.example.cherry_be.global.auth.OrgId;

@RestController
@RequestMapping("/api/targets")
@RequiredArgsConstructor
public class MemberController {

    // 기관 API 는 관심사별로 서비스가 나뉘어 있다.
    private final MemberService memberService;                            // 등록·목록·상세·삭제
    private final MemberLogService memberLogService;                      // 사건 이력
    private final MemberHealthAccessService memberHealthAccessService;    // 건강정보

    /**
     * 피보호자 등록
     * [POST] /api/targets
     */
    @PostMapping
    public ResponseEntity<String> registerMember(
            OrgId orgId,
            @Valid @RequestBody MemberRegisterRequest request) {
        Long savedId = memberService.registerMember(orgId, request);
        return ResponseEntity.ok("피보호자 등록 완료. ID: " + savedId);
    }

    /**
     * 전체 피보호자 조회 + 요약 통계
     * [GET] /api/targets
     */
    @GetMapping
    public ResponseEntity<MemberSummaryResponse> getTargets(OrgId orgId) {
        return ResponseEntity.ok(memberService.getTargets(orgId));
    }

    /**
     * 긴급 상태 피보호자만 조회
     * [GET] /api/targets/emergencies
     */
    @GetMapping("/emergencies")
    public ResponseEntity<List<MemberSummaryResponse.MemberInfo>> getEmergencies(
            OrgId orgId) {
        return ResponseEntity.ok(memberService.getEmergencies(orgId));
    }

    /**
     * 특정 피보호자 상세 조회
     * [GET] /api/targets/{targetId}
     */
    @GetMapping("/{targetId}")
    public ResponseEntity<MemberDetailResponse> getTargetDetail(
            OrgId orgId,
            @PathVariable Long targetId) {
        return ResponseEntity.ok(memberService.getTargetDetail(orgId, targetId));
    }

    /**
     * 피보호자 삭제
     * [DELETE] /api/targets/{targetId}
     */
    @DeleteMapping("/{targetId}")
    public ResponseEntity<String> deleteMember(
            OrgId orgId,
            @PathVariable Long targetId) {
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
            OrgId orgId,
            @PathVariable Long targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                memberLogService.getLogs(orgId, targetId, from, to, pageable));
    }


    /**
     * 피보호자 건강 정보 조회
     * [GET] /api/targets/{targetId}/health
     */
    @GetMapping("/{targetId}/health")
    public ResponseEntity<HealthResponse> getTargetHealth(
            OrgId orgId,
            @PathVariable Long targetId) {
        return ResponseEntity.ok(
                memberHealthAccessService.getHealth(orgId, targetId));
    }


    /**
     * 피보호자 건강 정보 전체 등록/수정
     * [PUT] /api/targets/{targetId}/health
     */
    @PutMapping("/{targetId}/health")
    public ResponseEntity<HealthResponse> putTargetHealth(
            OrgId orgId,
            @PathVariable Long targetId,
            @Valid @RequestBody HealthPutRequest request) {
        return ResponseEntity.ok(
                memberHealthAccessService.putHealth(orgId, targetId, request));
    }

    /**
     * 피보호자 건강 정보 부분 수정
     * [PATCH] /api/targets/{targetId}/health
     */
    @PatchMapping("/{targetId}/health")
    public ResponseEntity<HealthResponse> patchTargetHealth(
            OrgId orgId,
            @PathVariable Long targetId,
            @Valid @RequestBody HealthPatchRequest request) {
        return ResponseEntity.ok(
                memberHealthAccessService.patchHealth(orgId, targetId, request));
    }

    /**
     * [DELETE] /api/targets/{targetId}/health — 건강정보 삭제
     * 수정과 같은 권한이 필요하다. 저장값을 읽지 못하게 된 경우의 복구 경로이기도 하다.
     */
    @DeleteMapping("/{targetId}/health")
    public ResponseEntity<Void> deleteTargetHealth(
            OrgId orgId,
            @PathVariable Long targetId) {
        memberHealthAccessService.deleteHealth(orgId, targetId);
        return ResponseEntity.noContent().build();
    }
}
