package com.example.cherry_be.domain.member.service;

import com.example.cherry_be.domain.member.dto.MemberDetailResponse;
import com.example.cherry_be.domain.member.dto.MemberRegisterRequest;
import com.example.cherry_be.domain.member.dto.MemberSummaryResponse;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.entity.MemberStatus;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;

    // JWT의 orgId로 Organization을 찾는 공통 메서드
    private Organization findOrganization(String orgId) {
        return organizationRepository.findByOrgId(orgId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 기관입니다."));
    }

    /**
     * 피보호자 등록
     * [POST] /api/targets
     */
    @Transactional
    public Long registerMember(String orgId, MemberRegisterRequest request) {
        if (memberRepository.findByDeviceMac(request.getDeviceMac()).isPresent()) {
            throw new IllegalArgumentException("이미 등록된 디바이스입니다: " + request.getDeviceMac());
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
        Organization organization = findOrganization(orgId);
        Member member = memberRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 피보호자입니다."));

        // 해당 기관 소속인지 검증
        if (!member.getOrganization().getId().equals(organization.getId())) {
            throw new IllegalArgumentException("해당 기관의 피보호자가 아닙니다.");
        }

        return new MemberDetailResponse(member);
    }

    /**
     * 피보호자 삭제
     * [DELETE] /api/targets/{targetId}
     */
    @Transactional
    public void deleteMember(String orgId, Long targetId) {
        Organization organization = findOrganization(orgId);
        Member member = memberRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 피보호자입니다."));

        // 해당 기관 소속인지 검증
        if (!member.getOrganization().getId().equals(organization.getId())) {
            throw new IllegalArgumentException("해당 기관의 피보호자가 아닙니다.");
        }

        memberRepository.delete(member);
    }
}