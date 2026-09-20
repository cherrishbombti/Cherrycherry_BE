package com.example.cherry_be.domain.ward.service;

import com.example.cherry_be.domain.log.dto.LogPageResponse;
import com.example.cherry_be.domain.log.entity.Log;
import com.example.cherry_be.domain.log.entity.LogType;
import com.example.cherry_be.domain.log.repository.LogRepository;
import com.example.cherry_be.domain.log.service.LogQueryService;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.ward.dto.EmergencyLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import com.example.cherry_be.global.auth.GuardianEmail;

/**
 * 보호자가 보는 사건 이력 (#22, #24).
 * [GET] /api/wards/me/logs, [POST] /api/wards/me/emergency-log
 */
@Service
@RequiredArgsConstructor
public class WardLogService {

    private final WardFinder wardFinder;
    private final LogQueryService logQueryService;
    private final LogRepository logRepository;

    @Transactional(readOnly = true)
    public LogPageResponse getLogs(GuardianEmail oauthEmail, LocalDate from, LocalDate to, Pageable pageable) {
        return logQueryService.getLogs(wardFinder.getWard(oauthEmail), from, to, pageable);
    }

    /**
     * 119 신고 버튼 클릭 이력.
     *
     * 실제 통화 성공 여부는 서버가 알 수 없으므로 "버튼을 눌렀다"는 사실만 기록한다.
     * 프론트는 이 API 응답을 기다리지 않고 즉시 통화를 실행하므로,
     * 여기서 실패하더라도 통화 흐름에는 영향이 없어야 한다.
     */
    @Transactional
    public EmergencyLogResponse addEmergencyLog(GuardianEmail oauthEmail) {
        Member ward = wardFinder.getWard(oauthEmail);

        Log log = logRepository.save(Log.builder()
                .member(ward)
                .organization(ward.getOrganization()) // 기관번호로 연동하지 않았다면 null
                .status(ward.getStatus())             // 클릭 시점의 상태를 함께 기록
                .logType(LogType.EMERGENCY_CALL)
                .build());

        return EmergencyLogResponse.from(log);
    }
}
