package com.example.cherry_be.domain.ward.service;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.ward.dto.WardContactRequest;
import com.example.cherry_be.domain.ward.dto.WardContactResponse;
import com.example.cherry_be.domain.ward.entity.EmergencyContact;
import com.example.cherry_be.domain.ward.repository.EmergencyContactRepository;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import com.example.cherry_be.global.util.PhoneNumberUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import com.example.cherry_be.global.auth.GuardianEmail;

/**
 * 비상연락망. [GET/POST/PUT/DELETE] /api/wards/me/contacts
 */
@Service
@RequiredArgsConstructor
public class WardContactService {

    private static final int MAX_CONTACTS = 5;

    private final WardFinder wardFinder;
    private final EmergencyContactRepository emergencyContactRepository;

    @Transactional(readOnly = true)
    public List<WardContactResponse> getContacts(GuardianEmail oauthEmail) {
        Member ward = wardFinder.getWard(oauthEmail);
        return emergencyContactRepository.findByMemberOrderByPriorityAsc(ward).stream()
                .map(WardContactResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public WardContactResponse addContact(GuardianEmail oauthEmail, WardContactRequest request) {
        Member ward = wardFinder.getWard(oauthEmail);

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
                .phone(PhoneNumberUtils.normalize(request.getPhone()))
                .relationship(request.getRelationship())
                .priority(nextPriority)
                .build();

        return WardContactResponse.from(emergencyContactRepository.save(contact));
    }

    @Transactional
    public WardContactResponse updateContact(GuardianEmail oauthEmail, Long contactId, WardContactRequest request) {
        EmergencyContact contact = getOwnedContact(wardFinder.getWard(oauthEmail), contactId);
        contact.update(request.getName(),
                PhoneNumberUtils.normalize(request.getPhone()),
                request.getRelationship());
        return WardContactResponse.from(contact);
    }

    @Transactional
    public void deleteContact(GuardianEmail oauthEmail, Long contactId) {
        emergencyContactRepository.delete(getOwnedContact(wardFinder.getWard(oauthEmail), contactId));
    }

    /** 연락처를 찾고 현재 보호자의 피보호자 소유인지 검증한다. */
    private EmergencyContact getOwnedContact(Member ward, Long contactId) {
        EmergencyContact contact = emergencyContactRepository.findById(contactId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTACT_NOT_FOUND));
        if (!contact.getMember().getId().equals(ward.getId())) {
            throw new CustomException(ErrorCode.CONTACT_ACCESS_DENIED);
        }
        return contact;
    }
}
