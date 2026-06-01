package com.example.cherry_be.domain.ward.service;

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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WardService {

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final EmergencyContactRepository emergencyContactRepository;

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
}
