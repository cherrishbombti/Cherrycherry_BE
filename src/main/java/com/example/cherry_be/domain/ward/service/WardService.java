package com.example.cherry_be.domain.ward.service;

import com.example.cherry_be.domain.health.dto.HealthResponse;
import com.example.cherry_be.domain.health.dto.HealthUpsertRequest;
import com.example.cherry_be.domain.health.entity.UpdatedByType;
import com.example.cherry_be.domain.health.service.MemberHealthService;
import com.example.cherry_be.domain.log.dto.LogPageResponse;
import com.example.cherry_be.domain.log.entity.Log;
import com.example.cherry_be.domain.log.entity.LogType;
import com.example.cherry_be.domain.log.repository.LogRepository;
import com.example.cherry_be.domain.log.service.LogQueryService;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.repository.UserRepository;
import com.example.cherry_be.domain.ward.dto.*;
import com.example.cherry_be.domain.ward.entity.EmergencyContact;
import com.example.cherry_be.domain.ward.repository.EmergencyContactRepository;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WardService {

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final EmergencyContactRepository emergencyContactRepository;
    private final LogQueryService logQueryService;
    private final LogRepository logRepository;
    private final MemberHealthService memberHealthService;

    private User getGuardian(String oauthEmail) {
        return userRepository.findByOauthEmail(oauthEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private Member getWard(User guardian) {
        return memberRepository.findByUser(guardian)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * [POST] /api/wards/me — 피보호자 등록
     */
    @Transactional
    public Long registerWard(String oauthEmail, WardRegisterRequest request) {
        User guardian = getGuardian(oauthEmail);

        if (memberRepository.findByUser(guardian).isPresent()) {
            throw new CustomException(ErrorCode.WARD_ALREADY_EXISTS);
        }

        // deviceMac 중복 체크 (입력된 경우에만)
        if (request.getDeviceMac() != null && !request.getDeviceMac().isBlank()) {
            if (memberRepository.findByDeviceMac(request.getDeviceMac()).isPresent()) {
                throw new CustomException(ErrorCode.DEVICE_ALREADY_EXISTS);
            }
        }

        // birthDate(YYYY-MM-DD) → age 계산
        long age = 0;
        if (request.getBirthDate() != null && !request.getBirthDate().isBlank()) {
            LocalDate birth = LocalDate.parse(request.getBirthDate(), DateTimeFormatter.ISO_LOCAL_DATE);
            age = LocalDate.now().getYear() - birth.getYear();
        }

        Member member = Member.builder()
                .user(guardian)
                .organization(null)
                .name(request.getName())
                .age(age)
                .address(request.getAddress())
                .contact(request.getPhone())          // phone → contact 매핑
                .relationship(request.getRelationship())
                .deviceMac(request.getDeviceMac())
                .build();

        return memberRepository.save(member).getId();
    }

    /**
     * [GET] /api/wards/me/summary
     */
    @Transactional(readOnly = true)
    public WardSummaryResponse getSummary(String oauthEmail) {
        Member ward = getWard(getGuardian(oauthEmail));
        return WardSummaryResponse.from(ward);
    }

    /**
     * [GET] /api/wards/me/sensors
     */
    @Transactional(readOnly = true)
    public WardSensorResponse getSensors(String oauthEmail) {
        Member ward = getWard(getGuardian(oauthEmail));
        return WardSensorResponse.from(ward);
    }

    /**
     * [GET] /api/wards/me/contacts
     */
    @Transactional(readOnly = true)
    public List<WardContactResponse> getContacts(String oauthEmail) {
        Member ward = getWard(getGuardian(oauthEmail));
        return emergencyContactRepository.findByMember(ward).stream()
                .map(WardContactResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * [POST] /api/wards/me/contacts
     */
    @Transactional
    public WardContactResponse addContact(String oauthEmail, WardContactRequest request) {
        Member ward = getWard(getGuardian(oauthEmail));

        EmergencyContact contact = EmergencyContact.builder()
                .member(ward)
                .name(request.getName())
                .phone(request.getPhone())
                .relationship(request.getRelationship())
                .build();

        return WardContactResponse.from(emergencyContactRepository.save(contact));
    }

    /**
     * [GET] /api/wards/me/logs — 내 피보호자 낙상 이력 조회
     */
    @Transactional(readOnly = true)
    public LogPageResponse getLogs(String oauthEmail, LocalDate from, LocalDate to, Pageable pageable) {
        Member ward = getWard(getGuardian(oauthEmail));
        return logQueryService.getLogs(ward, from, to, pageable);
    }


    /**
     * [POST] /api/wards/me/emergency-log — 119 신고 버튼 클릭 이력 저장
     *
     * 실제 통화 성공 여부는 서버가 알 수 없으므로 "버튼을 눌렀다"는 사실만 기록한다.
     * 프론트는 이 API 응답을 기다리지 않고 즉시 통화를 실행하므로,
     * 여기서 실패하더라도 통화 흐름에는 영향이 없어야 한다.
     */
    @Transactional
    public EmergencyLogResponse addEmergencyLog(String oauthEmail) {
        Member ward = getWard(getGuardian(oauthEmail));

        Log log = logRepository.save(Log.builder()
                .member(ward)
                .organization(ward.getOrganization()) // 보호자 등록 피보호자는 null
                .status(ward.getStatus())             // 클릭 시점의 상태를 함께 기록
                .logType(LogType.EMERGENCY_CALL)
                .build());

        return EmergencyLogResponse.from(log);
    }


    // ── 건강정보 (#26) ──────────────────────────────

    /**
     * [GET] /api/wards/me/health
     */
    @Transactional(readOnly = true)
    public HealthResponse getHealth(String oauthEmail) {
        User guardian = getGuardian(oauthEmail);
        return memberHealthService.get(getWard(guardian));
    }

    /**
     * [PUT] /api/wards/me/health — 전체 등록/수정
     */
    @Transactional
    public HealthResponse putHealth(String oauthEmail, HealthUpsertRequest request) {
        User guardian = getGuardian(oauthEmail);
        return memberHealthService.put(getWard(guardian), request, toActor(guardian));
    }

    /**
     * [PATCH] /api/wards/me/health — 부분 수정
     */
    @Transactional
    public HealthResponse patchHealth(String oauthEmail, HealthUpsertRequest request) {
        User guardian = getGuardian(oauthEmail);
        return memberHealthService.patch(getWard(guardian), request, toActor(guardian));
    }

    private MemberHealthService.Actor toActor(User guardian) {
        return MemberHealthService.Actor.builder()
                .type(UpdatedByType.USER)
                .id(guardian.getId())
                .name(guardian.getName())
                .build();
    }

}
