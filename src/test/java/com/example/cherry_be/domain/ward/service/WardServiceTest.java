package com.example.cherry_be.domain.ward.service;

import com.example.cherry_be.domain.health.repository.MemberHealthRepository;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import com.example.cherry_be.domain.ward.dto.WardRegisterRequest;
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

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.cherry_be.global.auth.GuardianEmail;

/** 피보호자 등록과 현재 상태 조회. */
@ExtendWith(MockitoExtension.class)
class WardServiceTest {

    private static final GuardianEmail EMAIL = new GuardianEmail("guardian@example.com");

    @Mock private WardFinder wardFinder;
    @Mock private MemberRepository memberRepository;
    @Mock private MemberHealthRepository memberHealthRepository;

    @InjectMocks private WardService wardService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private User guardian;

    private final String json = """
            {"name":"김어르신","birthDate":"1940-03-05","address":"대구시",
             "phone":"010-1234-5678","relationship":"어머니","deviceMac":"AA:BB:CC:DD:EE:FF",
             "guardianName":"김보호","guardianPhone":"010-9999-8888"}""";

    @BeforeEach
    void setUp() {
        guardian = User.builder()
                .id(1L).oauthEmail(EMAIL.value()).name("보호자")
                .oauthProvider(SocialLoginType.GOOGLE).build();
        lenient().when(wardFinder.getGuardian(EMAIL)).thenReturn(guardian);
    }

    private WardRegisterRequest request(String body) throws Exception {
        return objectMapper.readValue(body, WardRegisterRequest.class);
    }

    @Test
    @DisplayName("이미 등록한 피보호자가 있으면 거부한다 (보호자당 1명)")
    void alreadyExists() throws Exception {
        when(wardFinder.hasWard(guardian)).thenReturn(true);

        assertThatThrownBy(() -> wardService.registerWard(EMAIL, request(json)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WARD_ALREADY_EXISTS);

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 사람이 쓰는 기기는 등록할 수 없다")
    void duplicateDeviceMac() throws Exception {
        when(memberRepository.findByDeviceMac("AA:BB:CC:DD:EE:FF"))
                .thenReturn(Optional.of(Member.builder().name("남의 피보호자").build()));

        assertThatThrownBy(() -> wardService.registerWard(EMAIL, request(json)))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEVICE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("전화번호는 숫자만 남겨 저장하고, 나이는 생년으로 계산한다")
    void normalizesPhoneAndComputesAge() throws Exception {
        when(memberRepository.findByDeviceMac(any())).thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        wardService.registerWard(EMAIL, request(json));

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
    @DisplayName("생년월일이 없으면 나이는 0")
    void ageZeroWhenBirthDateAbsent() throws Exception {
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        wardService.registerWard(EMAIL, request("""
                {"name":"김어르신","phone":"010-1234-5678"}"""));

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        assertThat(captor.getValue().getAge()).isZero();
    }

    @Test
    @DisplayName("기저질환을 함께 보내면 건강정보도 만든다")
    void createsHealthWhenDiseaseGiven() throws Exception {
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        wardService.registerWard(EMAIL, request("""
                {"name":"김어르신","phone":"010-1234-5678","disease":"고혈압"}"""));

        verify(memberHealthRepository).save(any());
    }

    @Test
    @DisplayName("기저질환이 없으면 건강정보를 만들지 않는다")
    void noHealthWhenDiseaseAbsent() throws Exception {
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        wardService.registerWard(EMAIL, request("""
                {"name":"김어르신","phone":"010-1234-5678"}"""));

        verify(memberHealthRepository, never()).save(any());
    }
}
