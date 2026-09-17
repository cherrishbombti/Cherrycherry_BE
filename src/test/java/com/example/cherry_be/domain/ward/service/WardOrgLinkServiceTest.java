package com.example.cherry_be.domain.ward.service;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.organization.repository.OrganizationRepository;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import com.example.cherry_be.domain.ward.dto.WardOrganizationRequest;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 기관 연동. 연동해도 소유자는 보호자로 남아야 한다. */
@ExtendWith(MockitoExtension.class)
class WardOrgLinkServiceTest {

    private static final String EMAIL = "guardian@example.com";

    @Mock private WardFinder wardFinder;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private WardOrgCodeAttemptLimiter orgCodeAttemptLimiter;

    @InjectMocks private WardOrgLinkService wardOrgLinkService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private User guardian;
    private Member ward;

    @BeforeEach
    void setUp() {
        guardian = User.builder()
                .id(1L).oauthEmail(EMAIL).name("보호자")
                .oauthProvider(SocialLoginType.GOOGLE).build();
        ward = Member.builder().user(guardian).name("김어르신").build();
        lenient().when(wardFinder.getGuardian(EMAIL)).thenReturn(guardian);
        lenient().when(wardFinder.getWard(guardian)).thenReturn(ward);
        lenient().when(wardFinder.getWard(anyString())).thenReturn(ward);
    }

    private WardOrganizationRequest request() throws Exception {
        return objectMapper.readValue("""
                {"orgCode":12345678}""", WardOrganizationRequest.class);
    }

    @Test
    @DisplayName("기관번호가 틀리면 실패로 기록하고 거부한다 (무차별 대입 방지)")
    void wrongCodeRecordsFailure() throws Exception {
        when(organizationRepository.findByOrgCode(12345678L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wardOrgLinkService.linkOrganization(EMAIL, request()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORG_CODE_NOT_FOUND);

        verify(orgCodeAttemptLimiter).recordFailure(guardian.getId());
        verify(orgCodeAttemptLimiter, never()).reset(anyLong());
    }

    @Test
    @DisplayName("성공하면 시도 횟수를 초기화하고, 소유자는 보호자로 남는다")
    void successResetsAttempts() throws Exception {
        Organization org = Organization.builder().orgId("org01").name("복지관").password("x").build();
        when(organizationRepository.findByOrgCode(12345678L)).thenReturn(Optional.of(org));

        wardOrgLinkService.linkOrganization(EMAIL, request());

        verify(orgCodeAttemptLimiter).reset(guardian.getId());
        assertThat(ward.getOrganization()).isEqualTo(org);
        assertThat(ward.getUser()).isEqualTo(guardian);
        // 기관이 연동돼도 관리 권한은 생기지 않는다 (조회만)
        assertThat(ward.isManageable()).isFalse();
    }

    @Test
    @DisplayName("잠긴 계정은 조회조차 하지 않는다")
    void lockedAccountIsRejectedBeforeLookup() throws Exception {
        org.mockito.Mockito.doThrow(new CustomException(ErrorCode.ORG_CODE_TOO_MANY_ATTEMPTS))
                .when(orgCodeAttemptLimiter).assertNotLocked(guardian.getId());

        assertThatThrownBy(() -> wardOrgLinkService.linkOrganization(EMAIL, request()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORG_CODE_TOO_MANY_ATTEMPTS);

        verify(organizationRepository, never()).findByOrgCode(anyLong());
    }

    @Test
    @DisplayName("연동돼 있지 않아도 해제는 오류가 아니다 (멱등)")
    void unlinkIsIdempotent() {
        assertThatCode(() -> wardOrgLinkService.unlinkOrganization(EMAIL)).doesNotThrowAnyException();
        assertThat(ward.getOrganization()).isNull();
    }
}
