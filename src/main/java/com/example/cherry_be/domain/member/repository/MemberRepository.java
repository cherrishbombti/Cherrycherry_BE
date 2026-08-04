package com.example.cherry_be.domain.member.repository;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.entity.MemberStatus;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByDeviceMac(String deviceMac);
    List<Member> findByOrganization(Organization organization);
    List<Member> findByOrganizationAndStatus(Organization organization, MemberStatus status);
    Optional<Member> findByUser(User user); // 보호자로 피보호자 찾기
}
