package com.example.cherry_be.domain.member.service;

import com.example.cherry_be.domain.health.dto.HealthPutRequest;
import com.example.cherry_be.domain.health.service.MemberHealthService;
import com.example.cherry_be.domain.log.service.LogQueryService;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.notification.service.NotificationService;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.organization.repository.OrganizationRepository;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import com.example.cherry_be.domain.ward.repository.EmergencyContactRepository;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 기관(사회복지사) 관점 API.
 *
 * 핵심은 두 겹의 권한이다.
 *  - 조회 권한: 내 기관에 연결된 피보호자만 볼 수 있다
 *  - 관리 권한: 그중에서도 기관이 직접 등록한 무연고자만 수정·삭제할 수 있다
 *
 * 둘 다 실패를 403 이 아닌 404 로 낸다. 403 은 "있긴 한데 권한이 없다"를 알려주어
 * id 를 훑어 존재 여부를 알아낼 수 있게 되기 때문이다(IDOR).
 * 이 테스트가 깨지면 그 방어가 뚫린 것이다.
 */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final String ORG_ID = "org01";

    @Mock private MemberRepository memberRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private EmergencyContactRepository emergencyContactRepository;
    @Mock private LogQueryService logQueryService;
    @Mock private MemberHealthService memberHealthService;
    @Mock private NotificationService notificationService;

    @InjectMocks private MemberService memberService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Organization myOrg;
    private Organization otherOrg;

    @BeforeEach
    void setUp() {
        myOrg = org(1L, ORG_ID);
        otherOrg = org(2L, "org02");
        lenient().when(organizationRepository.findByOrgId(ORG_ID)).thenReturn(Optional.of(myOrg));
    }

    private Organization org(Long id, String orgId) {
        Organization organization = Organization.builder()
                .orgId(orgId).name("복지관" + id).password("x").build();
        ReflectionTestUtils.setField(organization, "id", id);
        return organization;
    }

    private Member memberOf(Organization organization, User owner) {
        Member member = Member.builder()
                .organization(organization).user(owner).name("김어르신").build();
        ReflectionTestUtils.setField(member, "id", 10L);
        return member;
    }

    private final User guardian = User.builder()
            .id(1L).oauthEmail("g@example.com").name("보호자")
            .oauthProvider(SocialLoginType.GOOGLE).build();

    @Nested
    @DisplayName("조회 권한")
    class ViewPermission {

        @Test
        @DisplayName("없는 기관이면 ORG_NOT_FOUND")
        void unknownOrg() {
            when(organizationRepository.findByOrgId("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.getTargetDetail("ghost", 10L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORG_NOT_FOUND);
        }

        @Test
        @DisplayName("없는 피보호자는 MEMBER_NOT_FOUND")
        void unknownMember() {
            when(memberRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.getTargetDetail(ORG_ID, 404L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        @DisplayName("타 기관 피보호자는 403 이 아니라 404 다 (존재 여부를 숨긴다)")
        void otherOrgMemberIsNotFound() {
            when(memberRepository.findById(10L)).thenReturn(Optional.of(memberOf(otherOrg, null)));

            assertThatThrownBy(() -> memberService.getTargetDetail(ORG_ID, 10L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        @DisplayName("어느 기관에도 연결되지 않은 피보호자도 404 다")
        void unlinkedMemberIsNotFound() {
            when(memberRepository.findById(10L)).thenReturn(Optional.of(memberOf(null, guardian)));

            assertThatThrownBy(() -> memberService.getTargetDetail(ORG_ID, 10L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        @DisplayName("보호자가 등록하고 우리 기관에 연동한 피보호자는 조회할 수 있다")
        void guardianOwnedLinkedMemberIsViewable() {
            when(memberRepository.findById(10L)).thenReturn(Optional.of(memberOf(myOrg, guardian)));

            assertThat(memberService.getTargetDetail(ORG_ID, 10L)).isNotNull();
        }
    }

    @Nested
    @DisplayName("관리 권한 (조회보다 좁다)")
    class ManagePermission {

        @Test
        @DisplayName("보호자가 등록한 피보호자는 기관이 삭제할 수 없다 — 조회는 되지만 404 로 막는다")
        void cannotDeleteGuardianOwned() {
            Member guardianOwned = memberOf(myOrg, guardian);
            when(memberRepository.findById(10L)).thenReturn(Optional.of(guardianOwned));

            assertThatThrownBy(() -> memberService.deleteMember(ORG_ID, 10L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);

            verify(memberRepository, never()).delete(any());
        }

        @Test
        @DisplayName("기관이 직접 등록한 무연고자는 삭제할 수 있다")
        void canDeleteOrgOwned() {
            Member orgOwned = memberOf(myOrg, null);
            when(memberRepository.findById(10L)).thenReturn(Optional.of(orgOwned));

            memberService.deleteMember(ORG_ID, 10L);

            verify(memberRepository).delete(orgOwned);
        }

        @Test
        @DisplayName("삭제 시 알림·건강정보를 먼저 정리한다 (FK 제약으로 삭제가 막히지 않도록)")
        void deletesDependentsFirst() {
            Member orgOwned = memberOf(myOrg, null);
            when(memberRepository.findById(10L)).thenReturn(Optional.of(orgOwned));

            memberService.deleteMember(ORG_ID, 10L);

            org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                    notificationService, memberHealthService, memberRepository);
            order.verify(notificationService).deleteByMember(orgOwned);
            order.verify(memberHealthService).deleteByMember(orgOwned);
            order.verify(memberRepository).delete(orgOwned);
        }

        @Test
        @DisplayName("보호자가 등록한 피보호자의 건강정보는 기관이 수정할 수 없다")
        void cannotEditGuardianOwnedHealth() throws Exception {
            when(memberRepository.findById(10L)).thenReturn(Optional.of(memberOf(myOrg, guardian)));
            HealthPutRequest request = objectMapper.readValue("""
                    {"disease":"고혈압","medication":"","memo":""}""", HealthPutRequest.class);

            assertThatThrownBy(() -> memberService.putHealth(ORG_ID, 10L, request))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);

            verify(memberHealthService, never()).put(any(), any(), any());
        }

        @Test
        @DisplayName("건강정보 '조회'는 보호자 소유여도 가능하다 (조회와 관리는 다른 권한)")
        void canViewGuardianOwnedHealth() {
            when(memberRepository.findById(10L)).thenReturn(Optional.of(memberOf(myOrg, guardian)));

            memberService.getHealth(ORG_ID, 10L);

            verify(memberHealthService).get(any(Member.class));
        }
    }

    @Nested
    @DisplayName("피보호자 등록")
    class Register {

        @Test
        @DisplayName("이미 쓰이는 기기는 등록할 수 없다")
        void duplicateDevice() throws Exception {
            when(memberRepository.findByDeviceMac("AA:BB:CC:DD:EE:FF"))
                    .thenReturn(Optional.of(memberOf(otherOrg, null)));

            // 기관 등록 DTO 의 연락처 키는 보호자 쪽(phone)과 달리 contact 다.
            var request = objectMapper.readValue("""
                    {"name":"김어르신","deviceMac":"AA:BB:CC:DD:EE:FF","contact":"010-1234-5678"}""",
                    com.example.cherry_be.domain.member.dto.MemberRegisterRequest.class);

            assertThatThrownBy(() -> memberService.registerMember(ORG_ID, request))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEVICE_ALREADY_EXISTS);

            verify(memberRepository, never()).save(any());
        }
    }
}
