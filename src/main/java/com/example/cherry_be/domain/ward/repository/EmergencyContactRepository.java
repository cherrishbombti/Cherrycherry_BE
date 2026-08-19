package com.example.cherry_be.domain.ward.repository;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.ward.entity.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {

    // 특정 피보호자의 비상연락망 전체 조회
    List<EmergencyContact> findByMember(Member member);

    // 우선순위 오름차순 정렬 조회 (1순위부터) — C6
    List<EmergencyContact> findByMemberOrderByPriorityAsc(Member member);

    // 우선순위 계산용: 현재 등록된 연락처 수
    long countByMember(Member member);

    // 우선순위가 가장 높은(숫자 큰) 연락처 1건 — 다음 우선순위 계산용
    Optional<EmergencyContact> findTopByMemberOrderByPriorityDesc(Member member);
}