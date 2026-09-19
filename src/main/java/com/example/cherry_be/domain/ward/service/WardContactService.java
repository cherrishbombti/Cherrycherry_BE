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

    /**
     * 현재 보호자의 피보호자에게 속한 연락처를 찾는다. 아니면 404.
     *
     * 남의 연락처를 403 으로 돌려주면 "그 ID 는 존재한다" 는 사실이 새어 나간다.
     * 없는 ID 는 404, 남의 ID 는 403 이면 1 부터 훑어 남의 연락처 ID 범위와 개수를 알 수 있다.
     * 소유자를 조회 조건에 넣어 두 경우를 같은 응답으로 만든다.
     * (타인 리소스는 403 이 아닌 404 — 저장소 공통 규칙)
     */
    private EmergencyContact getOwnedContact(Member ward, Long contactId) {
        return emergencyContactRepository.findByIdAndMember(contactId, ward)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTACT_NOT_FOUND));
    }
}
