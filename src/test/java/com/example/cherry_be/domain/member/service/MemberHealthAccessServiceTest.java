package com.example.cherry_be.domain.member.service;

import com.example.cherry_be.domain.health.dto.HealthPutRequest;
import com.example.cherry_be.domain.health.entity.UpdatedByType;
import com.example.cherry_be.domain.health.service.MemberHealthService;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.cherry_be.global.auth.OrgId;

/**
 * 기관이 보는 건강정보.
 *
 * 조회와 수정이 서로 다른 권한을 쓴다는 점이 핵심이다.
 * 보호자가 등록한 피보호자의 건강정보는 볼 수는 있어도 고칠 수 없다.
 */
@ExtendWith(MockitoExtension.class)
class MemberHealthAccessServiceTest {

    private static final OrgId ORG_ID = new OrgId("org01");

    @Mock private MemberFinder memberFinder;
    @Mock private MemberHealthService memberHealthService;

    @InjectMocks private MemberHealthAccessService healthAccessService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Organization myOrg;
    private Member member;

    @BeforeEach
    void setUp() {
        myOrg = Organization.builder().orgId(ORG_ID.value()).name("복지관").password("x").build();
        ReflectionTestUtils.setField(myOrg, "id", 1L);
        member = Member.builder().organization(myOrg).name("무연고자").build();
        ReflectionTestUtils.setField(member, "id", 10L);
        lenient().when(memberFinder.getOrganization(ORG_ID)).thenReturn(myOrg);
    }

    private HealthPutRequest putRequest() throws Exception {
        return objectMapper.readValue("""
                {"disease":"고혈압","medication":"","memo":""}""", HealthPutRequest.class);
    }

    @Test
    @DisplayName("조회는 조회 권한만 있으면 된다")
    void viewUsesViewPermission() {
        when(memberFinder.getViewable(ORG_ID, 10L)).thenReturn(member);

        healthAccessService.getHealth(ORG_ID, 10L);

        verify(memberHealthService).get(member);
    }

    @Test
    @DisplayName("수정은 관리 권한을 요구한다 — 보호자 소유면 404 로 막힌다")
    void editRequiresManagePermission() throws Exception {
        when(memberFinder.getManaged(any(Organization.class), anyLong()))
                .thenThrow(new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        assertThatThrownBy(() -> healthAccessService.putHealth(ORG_ID, 10L, putRequest()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);

        verify(memberHealthService, never()).put(any(), any(), any());
    }

    @Test
    @DisplayName("수정자는 기관으로 기록된다 (보호자가 고친 것과 구분하기 위해)")
    void recordsOrganizationAsActor() throws Exception {
        when(memberFinder.getManaged(myOrg, 10L)).thenReturn(member);

        healthAccessService.putHealth(ORG_ID, 10L, putRequest());

        ArgumentCaptor<MemberHealthService.Actor> captor =
                ArgumentCaptor.forClass(MemberHealthService.Actor.class);
        verify(memberHealthService).put(any(), any(), captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(UpdatedByType.ORGANIZATION);
        assertThat(captor.getValue().getId()).isEqualTo(myOrg.getId());
    }

    @Test
    @DisplayName("삭제도 수정과 같은 권한을 요구한다")
    void deleteRequiresManagePermission() {
        when(memberFinder.getManaged(ORG_ID, 10L)).thenReturn(member);

        healthAccessService.deleteHealth(ORG_ID, 10L);

        verify(memberHealthService).deleteByMember(member);
    }
}
