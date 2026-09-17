package com.example.cherry_be.domain.ward.service;

import com.example.cherry_be.domain.health.repository.MemberHealthRepository;
import com.example.cherry_be.domain.health.service.MemberHealthService;
import com.example.cherry_be.domain.log.entity.Log;
import com.example.cherry_be.domain.log.entity.LogType;
import com.example.cherry_be.domain.log.repository.LogRepository;
import com.example.cherry_be.domain.log.service.LogQueryService;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.notification.service.NotificationService;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.organization.repository.OrganizationRepository;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import com.example.cherry_be.domain.user.repository.UserRepository;
import com.example.cherry_be.domain.ward.dto.WardContactRequest;
import com.example.cherry_be.domain.ward.dto.WardOrganizationRequest;
import com.example.cherry_be.domain.ward.dto.WardRegisterRequest;
import com.example.cherry_be.domain.ward.entity.EmergencyContact;
import com.example.cherry_be.domain.ward.repository.EmergencyContactRepository;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 보호자 관점 API.
 *
 * 이 클래스는 "요청한 보호자 → 그의 피보호자" 를 해석한 뒤 각 도메인에 위임하는 자리다.
 * 따라서 검증의 핵심은 위임 결과가 아니라 <b>경계</b>다.
 * 남의 자원에 닿지 않는가, 제한을 넘지 않는가, 저장 형식이 규칙대로인가.
 */
@ExtendWith(MockitoExtension.class)
class WardServiceTest {

    private static final String EMAIL = "guardian@example.com";

    @Mock private UserRepository userRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private EmergencyContactRepository emergencyContactRepository;
    @Mock private LogQueryService logQueryService;
    @Mock private LogRepository logRepository;
    @Mock private MemberHealthService memberHealthService;
    @Mock private MemberHealthRepository memberHealthRepository;
    @Mock private NotificationService notificationService;
    @Mock private WardOrgCodeAttemptLimiter orgCodeAttemptLimiter;

    @InjectMocks private WardService wardService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private User guardian;
    private Member ward;

    @BeforeEach
    void setUp() {
        guardian = User.builder()
                .id(1L).oauthEmail(EMAIL).name("보호자")
                .oauthProvider(SocialLoginType.GOOGLE).build();
        ward = Member.builder().user(guardian).name("김어르신").build();
        ReflectionTestUtils.setField(ward, "id", 10L);

        lenient().when(userRepository.findByOauthEmail(EMAIL)).thenReturn(Optional.of(guardian));
        lenient().when(memberRepository.findByUser(guardian)).thenReturn(Optional.of(ward));
    }

    private <T> T dto(String json, Class<T> type) throws Exception {
        return objectMapper.readValue(json, type);
    }

    @Nested
    @DisplayName("요청자 해석")
    class ResolveRequester {

        @Test
        @DisplayName("없는 계정이면 USER_NOT_FOUND")
        void unknownUser() {
            when(userRepository.findByOauthEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> wardService.getSummary("ghost@example.com"))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("피보호자를 등록하지 않았으면 MEMBER_NOT_FOUND")
        void noWardRegistered() {
            when(memberRepository.findByUser(guardian)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> wardService.getSummary(EMAIL))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("피보호자 등록")
    class RegisterWard {

        private final String json = """
                {"name":"김어르신","birthDate":"1940-03-05","address":"대구시",
                 "phone":"010-1234-5678","relationship":"어머니","deviceMac":"AA:BB:CC:DD:EE:FF",
                 "guardianName":"김보호","guardianPhone":"010-9999-8888"}""";

        @Test
        @DisplayName("이미 등록한 피보호자가 있으면 거부한다 (보호자당 1명)")
        void alreadyExists() throws Exception {
            assertThatThrownBy(() -> wardService.registerWard(EMAIL, dto(json, WardRegisterRequest.class)))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WARD_ALREADY_EXISTS);

            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("다른 사람이 쓰는 기기는 등록할 수 없다")
        void duplicateDeviceMac() throws Exception {
            when(memberRepository.findByUser(guardian)).thenReturn(Optional.empty());
            when(memberRepository.findByDeviceMac("AA:BB:CC:DD:EE:FF"))
                    .thenReturn(Optional.of(Member.builder().name("남의 피보호자").build()));

            assertThatThrownBy(() -> wardService.registerWard(EMAIL, dto(json, WardRegisterRequest.class)))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEVICE_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("전화번호는 숫자만 남겨 저장하고, 나이는 생년으로 계산한다")
        void normalizesPhoneAndComputesAge() throws Exception {
            when(memberRepository.findByUser(guardian)).thenReturn(Optional.empty());
            when(memberRepository.findByDeviceMac(any())).thenReturn(Optional.empty());
            when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

            wardService.registerWard(EMAIL, dto(json, WardRegisterRequest.class));

            ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
            verify(memberRepository).save(captor.capture());
            Member saved = captor.getValue();

            assertThat(saved.getContact()).isEqualTo("01012345678");
            assertThat(saved.getAge()).isEqualTo(LocalDate.now().getYear() - 1940);
            assertThat(saved.getUser()).isEqualTo(guardian);
            // 보호자가 등록했으므로 기관은 비어 있어야 한다 (소유자 구분)
            assertThat(saved.getOrganization()).isNull();
        }

        @Test
        @DisplayName("기저질환을 함께 보내면 건강정보도 만든다")
        void createsHealthWhenDiseaseGiven() throws Exception {
            when(memberRepository.findByUser(guardian)).thenReturn(Optional.empty());
            when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

            wardService.registerWard(EMAIL, dto("""
                    {"name":"김어르신","phone":"010-1234-5678","disease":"고혈압"}""",
                    WardRegisterRequest.class));

            verify(memberHealthRepository).save(any());
        }

        @Test
        @DisplayName("기저질환이 없으면 건강정보를 만들지 않는다")
        void noHealthWhenDiseaseAbsent() throws Exception {
            when(memberRepository.findByUser(guardian)).thenReturn(Optional.empty());
            when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

            wardService.registerWard(EMAIL, dto("""
                    {"name":"김어르신","phone":"010-1234-5678"}""", WardRegisterRequest.class));

            verify(memberHealthRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("비상연락망")
    class Contacts {

        private final String json = """
                {"name":"김보호","phone":"010-1111-2222","relationship":"아들"}""";

        @Test
        @DisplayName("최대 5개까지만 등록할 수 있다")
        void limit() throws Exception {
            when(emergencyContactRepository.countByMember(ward)).thenReturn(5L);

            assertThatThrownBy(() -> wardService.addContact(EMAIL, dto(json, WardContactRequest.class)))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTACT_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("우선순위는 현재 최대값 다음 번호를 쓴다 (삭제 후 재추가해도 안 겹침)")
        void priorityIsMaxPlusOne() throws Exception {
            when(emergencyContactRepository.countByMember(ward)).thenReturn(2L);
            when(emergencyContactRepository.findTopByMemberOrderByPriorityDesc(ward))
                    .thenReturn(Optional.of(EmergencyContact.builder()
                            .member(ward).name("기존").phone("01000000000").priority(7).build()));
            when(emergencyContactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            wardService.addContact(EMAIL, dto(json, WardContactRequest.class));

            ArgumentCaptor<EmergencyContact> captor = ArgumentCaptor.forClass(EmergencyContact.class);
            verify(emergencyContactRepository).save(captor.capture());
            assertThat(captor.getValue().getPriority()).isEqualTo(8);
            assertThat(captor.getValue().getPhone()).isEqualTo("01011112222");
        }

        @Test
        @DisplayName("첫 연락처의 우선순위는 1")
        void firstContactPriorityIsOne() throws Exception {
            when(emergencyContactRepository.countByMember(ward)).thenReturn(0L);
            when(emergencyContactRepository.findTopByMemberOrderByPriorityDesc(ward))
                    .thenReturn(Optional.empty());
            when(emergencyContactRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            wardService.addContact(EMAIL, dto(json, WardContactRequest.class));

            ArgumentCaptor<EmergencyContact> captor = ArgumentCaptor.forClass(EmergencyContact.class);
            verify(emergencyContactRepository).save(captor.capture());
            assertThat(captor.getValue().getPriority()).isEqualTo(1);
        }

        @Test
        @DisplayName("남의 피보호자 연락처는 수정할 수 없다")
        void cannotUpdateOthersContact() throws Exception {
            Member otherWard = Member.builder().name("남의 피보호자").build();
            ReflectionTestUtils.setField(otherWard, "id", 99L);
            EmergencyContact othersContact = EmergencyContact.builder()
                    .member(otherWard).name("남").phone("01000000000").priority(1).build();
            when(emergencyContactRepository.findById(5L)).thenReturn(Optional.of(othersContact));

            assertThatThrownBy(() ->
                    wardService.updateContact(EMAIL, 5L, dto(json, WardContactRequest.class)))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTACT_ACCESS_DENIED);
        }

        @Test
        @DisplayName("남의 피보호자 연락처는 삭제할 수 없다")
        void cannotDeleteOthersContact() {
            Member otherWard = Member.builder().name("남의 피보호자").build();
            ReflectionTestUtils.setField(otherWard, "id", 99L);
            when(emergencyContactRepository.findById(5L)).thenReturn(Optional.of(
                    EmergencyContact.builder().member(otherWard).name("남")
                            .phone("01000000000").priority(1).build()));

            assertThatThrownBy(() -> wardService.deleteContact(EMAIL, 5L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTACT_ACCESS_DENIED);
            verify(emergencyContactRepository, never()).delete(any());
        }

        @Test
        @DisplayName("없는 연락처는 CONTACT_NOT_FOUND")
        void contactNotFound() {
            when(emergencyContactRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> wardService.deleteContact(EMAIL, 404L))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTACT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("기관 연동")
    class OrgLink {

        private final String json = """
                {"orgCode":"12345678"}""";

        @Test
        @DisplayName("기관번호가 틀리면 실패로 기록하고 거부한다 (무차별 대입 방지)")
        void wrongCodeRecordsFailure() throws Exception {
            when(organizationRepository.findByOrgCode(12345678L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    wardService.linkOrganization(EMAIL, dto(json, WardOrganizationRequest.class)))
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

            wardService.linkOrganization(EMAIL, dto(json, WardOrganizationRequest.class));

            verify(orgCodeAttemptLimiter).reset(guardian.getId());
            assertThat(ward.getOrganization()).isEqualTo(org);
            assertThat(ward.getUser()).isEqualTo(guardian);
            assertThat(ward.isManageable()).isFalse();
        }

        @Test
        @DisplayName("연동돼 있지 않아도 해제는 오류가 아니다 (멱등)")
        void unlinkIsIdempotent() {
            assertThatCode(() -> wardService.unlinkOrganization(EMAIL)).doesNotThrowAnyException();
            assertThat(ward.getOrganization()).isNull();
        }
    }

    @Nested
    @DisplayName("119 신고 이력")
    class EmergencyLog {

        @Test
        @DisplayName("클릭 시점의 상태와 함께 EMERGENCY_CALL 로 남긴다")
        void recordsClick() {
            when(logRepository.save(any(Log.class))).thenAnswer(inv -> inv.getArgument(0));

            wardService.addEmergencyLog(EMAIL);

            ArgumentCaptor<Log> captor = ArgumentCaptor.forClass(Log.class);
            verify(logRepository).save(captor.capture());
            assertThat(captor.getValue().getLogType()).isEqualTo(LogType.EMERGENCY_CALL);
            assertThat(captor.getValue().getStatus()).isEqualTo(ward.getStatus());
            assertThat(captor.getValue().getMember()).isEqualTo(ward);
        }
    }
}
