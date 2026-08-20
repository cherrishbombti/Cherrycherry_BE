package com.example.cherry_be.domain.ward.service;

import com.example.cherry_be.domain.health.dto.HealthResponse;
import com.example.cherry_be.domain.health.dto.HealthPatchRequest;
import com.example.cherry_be.domain.health.dto.HealthPutRequest;
import com.example.cherry_be.domain.health.entity.UpdatedByType;
import com.example.cherry_be.domain.health.service.MemberHealthService;
import com.example.cherry_be.domain.log.dto.LogPageResponse;
import com.example.cherry_be.domain.notification.dto.NotificationPageResponse;
import com.example.cherry_be.domain.notification.service.NotificationService;
import com.example.cherry_be.domain.log.entity.Log;
import com.example.cherry_be.domain.log.entity.LogType;
import com.example.cherry_be.domain.log.repository.LogRepository;
import com.example.cherry_be.domain.log.service.LogQueryService;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.organization.repository.OrganizationRepository;
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
    private final OrganizationRepository organizationRepository;
    private final EmergencyContactRepository emergencyContactRepository;
    private final LogQueryService logQueryService;
    private final LogRepository logRepository;
    private final MemberHealthService memberHealthService;
    private final NotificationService notificationService;

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

    // ── 기관 연동 (#45) ─────────────────────────────
    //
    // 보호자가 기관번호를 입력하면 피보호자에 기관을 연결한다.
    // 소유자는 그대로 보호자이며, 기관은 대시보드에서 조회만 할 수 있다.

    /**
     * [GET] /api/wards/me/organization — 현재 연동 상태 조회
     */
    @Transactional(readOnly = true)
    public WardOrganizationResponse getOrganization(String oauthEmail) {
        Member ward = getWard(getGuardian(oauthEmail));
        return ward.getOrganization() == null
                ? WardOrganizationResponse.notLinked()
                : WardOrganizationResponse.from(ward.getOrganization());
    }

    /**
     * [PATCH] /api/wards/me/organization — 기관 연동
     *
     * 이미 다른 기관과 연동돼 있으면 새 기관으로 교체한다.
     * 피보호자의 소유자는 보호자이므로 어느 기관에 보일지도 보호자가 정한다.
     */
    @Transactional
    public WardOrganizationResponse linkOrganization(String oauthEmail, WardOrganizationRequest request) {
        Member ward = getWard(getGuardian(oauthEmail));

        Organization organization = organizationRepository.findByOrgCode(request.getOrgCode())
                .orElseThrow(() -> new CustomException(ErrorCode.ORG_CODE_NOT_FOUND));

        ward.linkOrganization(organization);
        return WardOrganizationResponse.from(organization);
    }

    /**
     * [DELETE] /api/wards/me/organization — 기관 연동 해제
     * 연동돼 있지 않아도 오류로 보지 않는다(멱등).
     */
    @Transactional
    public void unlinkOrganization(String oauthEmail) {
        getWard(getGuardian(oauthEmail)).unlinkOrganization();
    }

    /**
     * [GET] /api/wards/me/contacts
     */
    @Transactional(readOnly = true)
    public List<WardContactResponse> getContacts(String oauthEmail) {
        Member ward = getWard(getGuardian(oauthEmail));
        return emergencyContactRepository.findByMemberOrderByPriorityAsc(ward).stream()
                .map(WardContactResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * [POST] /api/wards/me/contacts
     */
    private static final int MAX_CONTACTS = 5;

    @Transactional
    public WardContactResponse addContact(String oauthEmail, WardContactRequest request) {
        Member ward = getWard(getGuardian(oauthEmail));

        // 개수 제한 (비상 연락망은 최대 5개)
        if (emergencyContactRepository.countByMember(ward) >= MAX_CONTACTS) {
            throw new CustomException(ErrorCode.CONTACT_LIMIT_EXCEEDED);
        }

        // 우선순위 = 현재 최대값 + 1 (삭제 후 재추가해도 번호가 겹치지 않음)
        int nextPriority = emergencyContactRepository
                .findTopByMemberOrderByPriorityDesc(ward)
                .map(c -> c.getPriority() + 1)
                .orElse(1);

        EmergencyContact contact = EmergencyContact.builder()
                .member(ward)
                .name(request.getName())
                .phone(request.getPhone())
                .relationship(request.getRelationship())
                .priority(nextPriority)
                .build();

        return WardContactResponse.from(emergencyContactRepository.save(contact));
    }

    // 연락처를 찾고 현재 보호자의 피보호자 소유인지 검증
    private EmergencyContact getOwnedContact(Member ward, Long contactId) {
        EmergencyContact contact = emergencyContactRepository.findById(contactId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTACT_NOT_FOUND));
        if (!contact.getMember().getId().equals(ward.getId())) {
            throw new CustomException(ErrorCode.CONTACT_ACCESS_DENIED);
        }
        return contact;
    }

    /** [PUT] /api/wards/me/contacts/{id} */
    @Transactional
    public WardContactResponse updateContact(String oauthEmail, Long contactId, WardContactRequest request) {
        Member ward = getWard(getGuardian(oauthEmail));
        EmergencyContact contact = getOwnedContact(ward, contactId);
        contact.update(request.getName(), request.getPhone(), request.getRelationship());
        return WardContactResponse.from(contact);
    }

    /** [DELETE] /api/wards/me/contacts/{id} */
    @Transactional
    public void deleteContact(String oauthEmail, Long contactId) {
        Member ward = getWard(getGuardian(oauthEmail));
        EmergencyContact contact = getOwnedContact(ward, contactId);
        emergencyContactRepository.delete(contact);
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
                .organization(ward.getOrganization()) // 기관번호로 연동하지 않았다면 null
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
    public HealthResponse putHealth(String oauthEmail, HealthPutRequest request) {
        User guardian = getGuardian(oauthEmail);
        return memberHealthService.put(getWard(guardian), request, toActor(guardian));
    }

    /**
     * [PATCH] /api/wards/me/health — 부분 수정
     */
    @Transactional
    public HealthResponse patchHealth(String oauthEmail, HealthPatchRequest request) {
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


    // ── 알림함 (#25) ────────────────────────────────

    /**
     * [GET] /api/wards/me/notifications
     */
    @Transactional(readOnly = true)
    public NotificationPageResponse getNotifications(String oauthEmail, Pageable pageable) {
        return notificationService.getNotifications(getGuardian(oauthEmail), pageable);
    }

    /**
     * [PATCH] /api/wards/me/notifications/{id}/read
     */
    @Transactional
    public void readNotification(String oauthEmail, Long notificationId) {
        notificationService.markAsRead(getGuardian(oauthEmail), notificationId);
    }

    /**
     * [PATCH] /api/wards/me/notifications/read-all
     */
    @Transactional
    public int readAllNotifications(String oauthEmail) {
        return notificationService.markAllAsRead(getGuardian(oauthEmail));
    }

}
