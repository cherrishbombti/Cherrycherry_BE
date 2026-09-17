package com.example.cherry_be.domain.ward.service;

import com.example.cherry_be.domain.health.entity.MemberHealth;
import com.example.cherry_be.domain.health.entity.UpdatedByType;
import com.example.cherry_be.domain.health.repository.MemberHealthRepository;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.ward.dto.WardRegisterRequest;
import com.example.cherry_be.domain.ward.dto.WardSensorResponse;
import com.example.cherry_be.domain.ward.dto.WardSummaryResponse;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import com.example.cherry_be.global.util.PhoneNumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import com.example.cherry_be.global.auth.GuardianEmail;

/**
 * 보호자 관점의 피보호자 등록과 현재 상태 조회.
 *
 * 보호자 API 는 관심사별로 나뉘어 있다.
 *  - 비상연락망      : WardContactService
 *  - 기관 연동       : WardOrgLinkService
 *  - 건강정보        : WardHealthService
 *  - 알림함          : WardNotificationService
 *  - 사건 이력       : WardLogService
 *
 * 공통으로 쓰는 "요청자 → 피보호자" 해석은 WardFinder 에 있다.
 */
@Service
@RequiredArgsConstructor
public class WardService {

    private final WardFinder wardFinder;
    private final MemberRepository memberRepository;
    private final MemberHealthRepository memberHealthRepository;

    /**
     * [POST] /api/wards/me — 피보호자 등록
     *
     * 보호자당 1명이며, 등록한 보호자가 소유자가 된다(organization 은 비워 둔다).
     * 기관이 보게 하려면 보호자가 별도로 기관번호를 입력해 연동한다(WardOrgLinkService).
     */
    @Transactional
    public Long registerWard(GuardianEmail oauthEmail, WardRegisterRequest request) {
        User guardian = wardFinder.getGuardian(oauthEmail);

        if (wardFinder.hasWard(guardian)) {
            throw new CustomException(ErrorCode.WARD_ALREADY_EXISTS);
        }

        // deviceMac 중복 체크 (입력된 경우에만)
        if (request.getDeviceMac() != null && !request.getDeviceMac().isBlank()) {
            if (memberRepository.findByDeviceMac(request.getDeviceMac()).isPresent()) {
                throw new CustomException(ErrorCode.DEVICE_ALREADY_EXISTS);
            }
        }

        // 보호자 이름·연락처 저장 (연락처는 숫자만)
        guardian.updateProfile(
                request.getGuardianName(),
                request.getGuardianPhone() == null
                        ? null
                        : PhoneNumberUtils.normalize(request.getGuardianPhone()));

        Member member = memberRepository.save(Member.builder()
                .user(guardian)
                .organization(null)
                .name(request.getName())
                .age(toAge(request.getBirthDate()))
                .address(request.getAddress())
                .contact(PhoneNumberUtils.normalize(request.getPhone()))  // 숫자만 남겨 저장
                .relationship(request.getRelationship())
                .deviceMac(request.getDeviceMac())
                .build());

        // 기저질환을 함께 받았으면 건강정보도 같이 만든다
        if (request.getDisease() != null && !request.getDisease().isBlank()) {
            memberHealthRepository.save(MemberHealth.builder()
                    .member(member)
                    .disease(request.getDisease())
                    .medication("")
                    .memo("")
                    .updatedByType(UpdatedByType.USER)
                    .updatedById(guardian.getId())
                    .updatedByName(guardian.getName())
                    .build());
        }

        return member.getId();
    }

    /** [GET] /api/wards/me/summary */
    @Transactional(readOnly = true)
    public WardSummaryResponse getSummary(GuardianEmail oauthEmail) {
        return WardSummaryResponse.from(wardFinder.getWard(oauthEmail));
    }

    /** [GET] /api/wards/me/sensors */
    @Transactional(readOnly = true)
    public WardSensorResponse getSensors(GuardianEmail oauthEmail) {
        return WardSensorResponse.from(wardFinder.getWard(oauthEmail));
    }

    /** birthDate(YYYY-MM-DD) → 나이. 값이 없으면 0. */
    private long toAge(String birthDate) {
        if (birthDate == null || birthDate.isBlank()) {
            return 0;
        }
        LocalDate birth = LocalDate.parse(birthDate, DateTimeFormatter.ISO_LOCAL_DATE);
        return LocalDate.now().getYear() - birth.getYear();
    }
}
