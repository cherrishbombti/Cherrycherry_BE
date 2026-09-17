package com.example.cherry_be.domain.member.service;

import com.example.cherry_be.domain.health.service.MemberHealthService;
import com.example.cherry_be.domain.member.dto.MemberRegisterRequest;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.notification.service.NotificationService;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.ward.repository.EmergencyContactRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 기관의 피보호자 등록·삭제. 권한 해석 자체는 MemberFinderTest 가 본다. */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final String ORG_ID = "org01";

    @Mock private MemberFinder memberFinder;
    @Mock private MemberRepository memberRepository;
    @Mock private EmergencyContactRepository emergencyContactRepository;
    @Mock private MemberHealthService memberHealthService;
    @Mock private NotificationService notificationService;

    @InjectMocks private MemberService memberService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Organization myOrg;

    @BeforeEach
    void setUp() {
        myOrg = Organization.builder().orgId(ORG_ID).name("복지관").password("x").build();
        ReflectionTestUtils.setField(myOrg, "id", 1L);
        lenient().when(memberFinder.getOrganization(ORG_ID)).thenReturn(myOrg);
    }

    private Member orgOwned() {
        Member member = Member.builder().organization(myOrg).user(null).name("무연고자").build();
        ReflectionTestUtils.setField(member, "id", 10L);
        return member;
    }

    @Test
    @DisplayName("이미 쓰이는 기기는 등록할 수 없다")
    void duplicateDevice() throws Exception {
        when(memberRepository.findByDeviceMac("AA:BB:CC:DD:EE:FF"))
                .thenReturn(Optional.of(orgOwned()));

        // 기관 등록 DTO 의 연락처 키는 보호자 쪽(phone)과 달리 contact 다.
        MemberRegisterRequest request = objectMapper.readValue("""
                {"name":"김어르신","deviceMac":"AA:BB:CC:DD:EE:FF","contact":"010-1234-5678"}""",
                MemberRegisterRequest.class);

        assertThatThrownBy(() -> memberService.registerMember(ORG_ID, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEVICE_ALREADY_EXISTS);

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("기관이 등록한 피보호자는 소유자가 기관이다 (user 는 비운다)")
    void registeredMemberIsOrgOwned() throws Exception {
        when(memberRepository.findByDeviceMac(any())).thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> {
            Member m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 10L);
            return m;
        });

        memberService.registerMember(ORG_ID, objectMapper.readValue("""
                {"name":"무연고자","deviceMac":"AA:BB:CC:DD:EE:FF","contact":"010-1234-5678"}""",
                MemberRegisterRequest.class));

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isNull();
        assertThat(captor.getValue().getOrganization()).isEqualTo(myOrg);
        assertThat(captor.getValue().isManageable()).isTrue();
        assertThat(captor.getValue().getContact()).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("삭제는 관리 권한을 거친다 — 권한이 없으면 지우지 않는다")
    void deleteGoesThroughManagePermission() {
        when(memberFinder.getManaged(anyString(), anyLong()))
                .thenThrow(new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        assertThatThrownBy(() -> memberService.deleteMember(ORG_ID, 10L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);

        verify(memberRepository, never()).delete(any());
    }

    @Test
    @DisplayName("삭제 시 알림·건강정보를 먼저 정리한다 (FK 제약으로 삭제가 막히지 않도록)")
    void deletesDependentsFirst() {
        Member member = orgOwned();
        when(memberFinder.getManaged(ORG_ID, 10L)).thenReturn(member);

        memberService.deleteMember(ORG_ID, 10L);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                notificationService, memberHealthService, memberRepository);
        order.verify(notificationService).deleteByMember(member);
        order.verify(memberHealthService).deleteByMember(member);
        order.verify(memberRepository).delete(member);
    }
}
