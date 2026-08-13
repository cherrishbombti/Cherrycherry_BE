package com.example.cherry_be.domain.member.service;

import com.example.cherry_be.domain.member.dto.MemberDetailResponse;
import com.example.cherry_be.domain.member.dto.MemberRegisterRequest;
import com.example.cherry_be.domain.member.dto.MemberSummaryResponse;
import com.example.cherry_be.domain.health.dto.HealthResponse;
import com.example.cherry_be.domain.health.dto.HealthPatchRequest;
import com.example.cherry_be.domain.health.dto.HealthPutRequest;
import com.example.cherry_be.domain.health.entity.UpdatedByType;
import com.example.cherry_be.domain.health.service.MemberHealthService;
import com.example.cherry_be.domain.log.dto.LogPageResponse;
import com.example.cherry_be.domain.log.service.LogQueryService;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.entity.MemberStatus;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.organization.repository.OrganizationRepository;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final LogQueryService logQueryService;
    private final MemberHealthService memberHealthService;

    // JWT의 orgId로 Organization을 찾는 공통 메서드
    private Organization findOrganization(String orgId) {
        return organizationRepository.findByOrgId(orgId)
                .orElseThrow(() -> new CustomException(ErrorCode.ORG_NOT_FOUND));
    }

    /**
     * 피보호자 등록
     * [POST] /api/targets
     */
    @Transactional
    public Long registerMember(String orgId, MemberRegisterRequest request) {
        if (memberRepository.findByDeviceMac(request.getDeviceMac()).isPresent()) {
            throw new CustomException(ErrorCode.DEVICE_ALREADY_EXISTS);
        }
        Organization organization = findOrganization(orgId);
        Member member = Member.builder()
                .organization(organization)
                .user(null)
                .name(request.getName())
                .age(request.getAge())
                .address(request.getAddress())
                .contact(request.getContact())
                .deviceMac(request.getDeviceMac())
                .build();
        return memberRepository.save(member).getId();
    }

    /**
     * 전체 피보호자 조회 + 요약 통계
     * [GET] /api/targets
     */
    @Transactional(readOnly = true)
    public MemberSummaryResponse getTargets(String orgId) {
        Organization organization = findOrganization(orgId);
        List<Member> members = memberRepository.findByOrganization(organization);
        return new MemberSummaryResponse(members);
    }

    /**
     * 긴급 상태 피보호자만 조회
     * [GET] /api/targets/emergencies
     */
    @Transactional(readOnly = true)
    public List<MemberSummaryResponse.MemberInfo> getEmergencies(String orgId) {
        Organization organization = findOrganization(orgId);
        List<Member> dangers = memberRepository.findByOrganizationAndStatus(organization, MemberStatus.DANGER);
        return dangers.stream().map(MemberSummaryResponse.MemberInfo::new).toList();
    }

    /**
     * 특정 피보호자 상세 조회
     * [GET] /api/targets/{targetId}
     */
    @Transactional(readOnly = true)
    public MemberDetailResponse getTargetDetail(String orgId, Long targetId) {
        Member member = findOwnedMember(findOrganization(orgId), targetId, orgId);
        return new MemberDetailResponse(member);
    }

    /**
     * 피보호자 삭제
     * [DELETE] /api/targets/{targetId}
     */
    @Transactional
    public void deleteMember(String orgId, Long targetId) {
        Member member = findOwnedMember(findOrganization(orgId), targetId, orgId);
        memberRepository.delete(member);
    }

    /**
     * [GET] /api/targets/{targetId}/logs — 특정 피보호자 낙상 이력 조회
     * 소속 검증 실패 시 존재하지 않는 경우와 동일하게 404 반환
     */
    @Transactional(readOnly = true)
    public LogPageResponse getLogs(String orgId, Long targetId,
                                   LocalDate from, LocalDate to, Pageable pageable) {
        Member member = findOwnedMember(findOrganization(orgId), targetId, orgId);
        return logQueryService.getLogs(member, from, to, pageable);
    }


    /**
     * [GET] /api/targets/{targetId}/health — 기관의 피보호자 건강정보 조회
     * 소속 검증 실패 시 존재하지 않는 경우와 동일하게 404
     */
    @Transactional(readOnly = true)
    public HealthResponse getHealth(String orgId, Long targetId) {
        Member member = findOwnedMember(findOrganization(orgId), targetId, orgId);
        return memberHealthService.get(member);
    }

    /**
     * [PUT] /api/targets/{targetId}/health — 전체 등록/수정
     */
    @Transactional
    public HealthResponse putHealth(String orgId, Long targetId, HealthPutRequest request) {
        Organization organization = findOrganization(orgId);
        Member member = findOwnedMember(organization, targetId, orgId);
        return memberHealthService.put(member, request, toActor(organization));
    }

    /**
     * [PATCH] /api/targets/{targetId}/health — 부분 수정
     */
    @Transactional
    public HealthResponse patchHealth(String orgId, Long targetId, HealthPatchRequest request) {
        Organization organization = findOrganization(orgId);
        Member member = findOwnedMember(organization, targetId, orgId);
        return memberHealthService.patch(member, request, toActor(organization));
    }

    private MemberHealthService.Actor toActor(Organization organization) {
        return MemberHealthService.Actor.builder()
                .type(UpdatedByType.ORGANIZATION)
                .id(organization.getId())
                .name(organization.getName())
                .build();
    }

    /**
     * 기관 소속 피보호자 조회 + 소속 검증 (공통).
     *
     * 소속이 아니면 존재하지 않는 경우와 동일하게 404를 반환한다.
     * 403을 주면 "존재하지만 볼 수 없다"는 사실이 드러나 ID 스캔이 가능해지므로(IDOR),
     * 실제 사유는 서버 로그에만 남긴다.
     */
    private Member findOwnedMember(Organization organization, Long targetId, String orgId) {
        Member member = memberRepository.findById(targetId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        if (member.getOrganization() == null
                || !member.getOrganization().getId().equals(organization.getId())) {
            log.warn("타 기관 피보호자 접근 시도 - orgId: {}, targetId: {}", orgId, targetId);
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return member;
    }


}
