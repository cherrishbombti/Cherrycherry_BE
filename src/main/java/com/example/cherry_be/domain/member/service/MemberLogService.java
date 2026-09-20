package com.example.cherry_be.domain.member.service;

import com.example.cherry_be.domain.log.dto.LogPageResponse;
import com.example.cherry_be.domain.log.service.LogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import com.example.cherry_be.global.auth.OrgId;

/**
 * 기관이 보는 피보호자 사건 이력 (#22).
 * [GET] /api/targets/{targetId}/logs
 */
@Service
@RequiredArgsConstructor
public class MemberLogService {

    private final MemberFinder memberFinder;
    private final LogQueryService logQueryService;

    /** 소속 검증 실패 시 존재하지 않는 경우와 동일하게 404. */
    @Transactional(readOnly = true)
    public LogPageResponse getLogs(OrgId orgId, Long targetId,
                                   LocalDate from, LocalDate to, Pageable pageable) {
        return logQueryService.getLogs(memberFinder.getViewable(orgId, targetId), from, to, pageable);
    }
}
