package com.example.cherry_be.domain.log.repository;

import com.example.cherry_be.domain.log.entity.Log;
import com.example.cherry_be.domain.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface LogRepository extends JpaRepository<Log, Long> {

    /**
     * 전체 이력 조회 (최신순 + 페이지네이션)
     */
    Page<Log> findByMemberOrderByDetectedAtDesc(Member member, Pageable pageable);

    /**
     * 기간 이력 조회 (최신순 + 페이지네이션)
     * from/to는 서비스에서 시각까지 보정해서 전달한다.
     */
    Page<Log> findByMemberAndDetectedAtBetweenOrderByDetectedAtDesc(
            Member member, LocalDateTime from, LocalDateTime to, Pageable pageable);
}
