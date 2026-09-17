package com.example.cherry_be.domain.member.service;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.organization.repository.OrganizationRepository;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 기관의 두 겹 권한 해석.
 *
 * 실패를 403 이 아닌 404 로 낸다는 규칙이 여기 고정된다. 403 은 "있지만 권한이 없다" 를
 * 알려주어 id 를 훑으면 존재 여부를 알아낼 수 있다(IDOR). 이 테스트가 깨지면 그 방어가 뚫린 것이다.
 */
@ExtendWith(MockitoExtension.class)
class MemberFinderTest {

    private static final String ORG_ID = "org01";

    @Mock private MemberRepository memberRepository;
    @Mock private OrganizationRepository organizationRepository;

    @InjectMocks private MemberFinder memberFinder;

    private Organization myOrg;
    private Organization otherOrg;

    private final User guardian = User.builder()
            .id(1L).oauthEmail("g@example.com").name("보호자")
            .oauthProvider(SocialLoginType.GOOGLE).build();

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

    @Nested
    @DisplayName("조회 권한")
    class ViewPermission {

        @Test
        @DisplayName("없는 기관이면 ORG_NOT_FOUND")
        void unknownOrg() {
            when(organizationRepository.findByOrgId("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberFinder.getViewable("ghost", 10L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORG_NOT_FOUND);
        }

        @Test
        @DisplayName("없는 피보호자는 MEMBER_NOT_FOUND")
        void unknownMember() {
            when(memberRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> memberFinder.getViewable(ORG_ID, 404L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        @DisplayName("타 기관 피보호자는 403 이 아니라 404 다 (존재 여부를 숨긴다)")
        void otherOrgMemberIsNotFound() {
            when(memberRepository.findById(10L)).thenReturn(Optional.of(memberOf(otherOrg, null)));

            assertThatThrownBy(() -> memberFinder.getViewable(ORG_ID, 10L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        @DisplayName("어느 기관에도 연결되지 않은 피보호자도 404 다")
        void unlinkedMemberIsNotFound() {
            when(memberRepository.findById(10L)).thenReturn(Optional.of(memberOf(null, guardian)));

            assertThatThrownBy(() -> memberFinder.getViewable(ORG_ID, 10L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        @DisplayName("보호자가 등록하고 우리 기관에 연동한 피보호자는 조회할 수 있다")
        void guardianOwnedLinkedMemberIsViewable() {
            Member linked = memberOf(myOrg, guardian);
            when(memberRepository.findById(10L)).thenReturn(Optional.of(linked));

            assertThat(memberFinder.getViewable(ORG_ID, 10L)).isEqualTo(linked);
        }
    }

    @Nested
    @DisplayName("관리 권한 (조회보다 좁다)")
    class ManagePermission {

        @Test
        @DisplayName("보호자가 등록한 피보호자는 관리할 수 없다 — 조회는 되지만 404 로 막는다")
        void guardianOwnedIsNotManageable() {
            when(memberRepository.findById(10L)).thenReturn(Optional.of(memberOf(myOrg, guardian)));

            assertThatThrownBy(() -> memberFinder.getManaged(ORG_ID, 10L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
        }

        @Test
        @DisplayName("기관이 직접 등록한 무연고자는 관리할 수 있다")
        void orgOwnedIsManageable() {
            Member orgOwned = memberOf(myOrg, null);
            when(memberRepository.findById(10L)).thenReturn(Optional.of(orgOwned));

            assertThat(memberFinder.getManaged(ORG_ID, 10L)).isEqualTo(orgOwned);
        }
    }
}
