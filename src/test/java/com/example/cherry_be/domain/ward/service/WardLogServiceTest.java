package com.example.cherry_be.domain.ward.service;

import com.example.cherry_be.domain.log.entity.Log;
import com.example.cherry_be.domain.log.entity.LogType;
import com.example.cherry_be.domain.log.repository.LogRepository;
import com.example.cherry_be.domain.log.service.LogQueryService;
import com.example.cherry_be.domain.member.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 보호자가 보는 사건 이력. */
@ExtendWith(MockitoExtension.class)
class WardLogServiceTest {

    private static final String EMAIL = "guardian@example.com";

    @Mock private WardFinder wardFinder;
    @Mock private LogQueryService logQueryService;
    @Mock private LogRepository logRepository;

    @InjectMocks private WardLogService wardLogService;

    private Member ward;

    @BeforeEach
    void setUp() {
        ward = Member.builder().name("김어르신").build();
        ReflectionTestUtils.setField(ward, "id", 10L);
        lenient().when(wardFinder.getWard(anyString())).thenReturn(ward);
    }

    @Test
    @DisplayName("119 클릭은 그 시점의 상태와 함께 EMERGENCY_CALL 로 남는다")
    void recordsEmergencyClick() {
        when(logRepository.save(any(Log.class))).thenAnswer(inv -> inv.getArgument(0));

        wardLogService.addEmergencyLog(EMAIL);

        ArgumentCaptor<Log> captor = ArgumentCaptor.forClass(Log.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getLogType()).isEqualTo(LogType.EMERGENCY_CALL);
        assertThat(captor.getValue().getStatus()).isEqualTo(ward.getStatus());
        assertThat(captor.getValue().getMember()).isEqualTo(ward);
    }

    @Test
    @DisplayName("기관과 연동돼 있지 않으면 로그의 기관은 비어 있다")
    void organizationIsNullWhenNotLinked() {
        when(logRepository.save(any(Log.class))).thenAnswer(inv -> inv.getArgument(0));

        wardLogService.addEmergencyLog(EMAIL);

        ArgumentCaptor<Log> captor = ArgumentCaptor.forClass(Log.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getOrganization()).isNull();
    }

    @Test
    @DisplayName("이력 조회는 내 피보호자로 범위를 좁혀 위임한다")
    void delegatesLogQueryScopedToMyWard() {
        wardLogService.getLogs(EMAIL, null, null, org.springframework.data.domain.PageRequest.of(0, 10));

        verify(logQueryService).getLogs(org.mockito.ArgumentMatchers.eq(ward),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                any());
    }
}
