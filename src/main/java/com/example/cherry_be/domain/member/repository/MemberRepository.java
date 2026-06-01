package com.example.cherry_be.domain.member.repository;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.entity.MemberStatus;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // 라즈베리파이 device_mac으로 피보호자 찾기
    Optional<Member> findByDeviceMac(String deviceMac);

    // 기관 소속 전체 피보호자 조회 (대시보드)
    List<Member> findByOrganization(Organization organization);

    // 기관 소속 + 특정 상태 피보호자 조회 (긴급 목록 등)
    List<Member> findByOrganizationAndStatus(Organization organization, MemberStatus status);

    // 보호자(User)로 피보호자 조회 (보호자 API 전용)
    Optional<Member> findByUser(User user);
}
