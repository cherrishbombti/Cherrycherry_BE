package com.example.cherry_be.domain.health.repository;

import com.example.cherry_be.domain.health.entity.MemberHealth;
import com.example.cherry_be.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberHealthRepository extends JpaRepository<MemberHealth, Long> {

    Optional<MemberHealth> findByMember(Member member);
}
