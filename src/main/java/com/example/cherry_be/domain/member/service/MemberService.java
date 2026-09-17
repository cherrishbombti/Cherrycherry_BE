package com.example.cherry_be.domain.member.service;

import com.example.cherry_be.domain.health.service.MemberHealthService;
import com.example.cherry_be.domain.member.dto.MemberDetailResponse;
import com.example.cherry_be.domain.member.dto.MemberRegisterRequest;
import com.example.cherry_be.domain.member.dto.MemberSummaryResponse;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.entity.MemberStatus;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.notification.service.NotificationService;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.ward.dto.WardContactResponse;
import com.example.cherry_be.domain.ward.repository.EmergencyContactRepository;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import com.example.cherry_be.global.util.PhoneNumberUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.example.cherry_be.global.auth.OrgId;

/**
 * 기관(사회복지사) 관점의 피보호자 등록·목록·상세·삭제.
 *
 * 기관 API 는 관심사별로 나뉘어 있다.
 *  - 사건 이력  : MemberLogService
 *  - 건강정보   : MemberHealthAccessService
 *
 * 공통으로 쓰는 "요청 기관 → 접근 가능한 피보호자" 해석은 MemberFinder 에 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberFinder memberFinder;
    private final MemberRepository memberRepository;
    private final EmergencyContactRepository emergencyContactRepository;
    private final MemberHealthService memberHealthService;
    private final NotificationService notificationService;

    /**
     * 피보호자 등록
     * [POST] /api/targets
     *
     * 기관이 등록하므로 user 는 비운다. 이 피보호자는 기관 소유(무연고자)가 되어
     * 기관이 수정·삭제까지 할 수 있다.
     */
    @Transactional
    public Long registerMember(OrgId orgId, MemberRegisterRequest request) {
        if (memberRepository.findByDeviceMac(request.getDeviceMac()).isPresent()) {
            throw new CustomException(ErrorCode.DEVICE_ALREADY_EXISTS);
        }
        Organization organization = memberFinder.getOrganization(orgId);

        Member member = Member.builder()
                .organization(organization)
                .user(null)
                .name(request.getName())
                .age(request.getAge())
                .address(request.getAddress())
                .contact(PhoneNumberUtils.normalize(request.getContact()))
                .deviceMac(request.getDeviceMac())
                .build();
        return memberRepository.save(member).getId();
    }

    /**
     * 전체 피보호자 조회 + 요약 통계
     * [GET] /api/targets
     */
    @Transactional(readOnly = true)
    public MemberSummaryResponse getTargets(OrgId orgId) {
        return new MemberSummaryResponse(
                memberRepository.findByOrganization(memberFinder.getOrganization(orgId)));
    }

    /**
     * 긴급 상태 피보호자만 조회
     * [GET] /api/targets/emergencies
     */
    @Transactional(readOnly = true)
    public List<MemberSummaryResponse.MemberInfo> getEmergencies(OrgId orgId) {
        return memberRepository
                .findByOrganizationAndStatus(memberFinder.getOrganization(orgId), MemberStatus.DANGER)
                .stream()
                .map(MemberSummaryResponse.MemberInfo::new)
                .toList();
    }

    /**
     * 특정 피보호자 상세 조회
     * [GET] /api/targets/{targetId}
     */
    @Transactional(readOnly = true)
    public MemberDetailResponse getTargetDetail(OrgId orgId, Long targetId) {
        Member member = memberFinder.getViewable(orgId, targetId);

        List<WardContactResponse> contacts =
                emergencyContactRepository.findByMemberOrderByPriorityAsc(member).stream()
                        .map(WardContactResponse::from)
                        .toList();

        return new MemberDetailResponse(member, contacts);
    }

    /**
     * 피보호자 삭제
     * [DELETE] /api/targets/{targetId}
     *
     * 보호자가 등록한 피보호자는 기관이 삭제할 수 없다(getManaged).
     *
     * fall_log·emergency_contact 는 Member 의 cascade 로 함께 지워진다.
     * notification(건수가 많을 수 있음)과 member_health(암호화 컬럼이라 EAGER 로딩을
     * 피하려고 연관을 두지 않음)는 여기서 직접 지운다.
     * 남겨두면 member_id NOT NULL FK 에 걸려 삭제 자체가 409 로 실패한다.
     */
    @Transactional
    public void deleteMember(OrgId orgId, Long targetId) {
        Member member = memberFinder.getManaged(orgId, targetId);

        notificationService.deleteByMember(member);
        memberHealthService.deleteByMember(member);
        memberRepository.delete(member);
    }
}
