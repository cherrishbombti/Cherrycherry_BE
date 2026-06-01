package com.example.cherry_be.domain.ward.repository;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.ward.entity.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {

    // 특정 피보호자의 비상연락망 전체 조회
    List<EmergencyContact> findByMember(Member member);
}
