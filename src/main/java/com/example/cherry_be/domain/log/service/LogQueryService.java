package com.example.cherry_be.domain.log.service;

import com.example.cherry_be.domain.log.dto.LogPageResponse;
import com.example.cherry_be.domain.log.entity.Log;
import com.example.cherry_be.domain.log.repository.LogRepository;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 낙상 이력 조회 공통 로직.
 * 보호자(WardService)와 기관(MemberService) 양쪽에서 재사용한다.
 *
 * from/to 조합 규칙
 *  - 둘 다 없음 : 전체를 최신순 페이지네이션
 *  - 둘 다 있음 : from 00:00:00 ~ to 23:59:59 (양 끝 포함)
 *  - from만     : from 00:00:00 ~ 현재 시각
 *  - to만       : 과거 전체 ~ to 23:59:59
 * from > to 이면 400(INVALID_DATE_RANGE)
 */
@Service
@RequiredArgsConstructor
public class LogQueryService {

    // to만 주어졌을 때 하한으로 사용할 기준 시각
    private static final LocalDateTime MIN_DATE_TIME = LocalDate.of(1970, 1, 1).atStartOfDay();

    private final LogRepository logRepository;

    @Transactional(readOnly = true)
    public LogPageResponse getLogs(Member member, LocalDate from, LocalDate to, Pageable pageable) {

        // 기간 조건이 전혀 없으면 전체 조회
        if (from == null && to == null) {
            Page<Log> logs = logRepository.findByMemberOrderByDetectedAtDesc(member, pageable);
            return LogPageResponse.from(logs);
        }

        // from > to 검증 (둘 다 있을 때만 의미 있음)
        if (from != null && to != null && from.isAfter(to)) {
            throw new CustomException(ErrorCode.INVALID_DATE_RANGE);
        }

        // 경계 보정: from은 그날 00:00:00, to는 그날 23:59:59.999999999 (양 끝 포함)
        LocalDateTime start = (from != null) ? from.atStartOfDay() : MIN_DATE_TIME;
        LocalDateTime end = (to != null) ? to.atTime(java.time.LocalTime.MAX) : LocalDateTime.now();

        Page<Log> logs = logRepository
                .findByMemberAndDetectedAtBetweenOrderByDetectedAtDesc(member, start, end, pageable);
        return LogPageResponse.from(logs);
    }
}
