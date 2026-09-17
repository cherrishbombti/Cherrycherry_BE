package com.example.cherry_be.domain.ward.service;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.ward.dto.WardContactRequest;
import com.example.cherry_be.domain.ward.entity.EmergencyContact;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.example.cherry_be.global.auth.GuardianEmail;

/** 비상연락망. 소유권 검증이 핵심이다. */
@ExtendWith(MockitoExtension.class)
class WardContactServiceTest {

    private static final GuardianEmail EMAIL = new GuardianEmail("guardian@example.com");

    @Mock private WardFinder wardFinder;
    @Mock private EmergencyContactRepository emergencyContactRepository;

    @InjectMocks private WardContactService wardContactService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Member ward;

    private final String json = """
            {"name":"김보호","phone":"010-1111-2222","relationship":"아들"}""";

    @BeforeEach
    void setUp() {
        ward = Member.builder().name("김어르신").build();
        ReflectionTestUtils.setField(ward, "id", 10L);
        lenient().when(wardFinder.getWard(any(GuardianEmail.class))).thenReturn(ward);
    }

    private WardContactRequest request() throws Exception {
        return objectMapper.readValue(json, WardContactRequest.class);
    }

    private Member otherWard() {
        Member other = Member.builder().name("남의 피보호자").build();
        ReflectionTestUtils.setField(other, "id", 99L);
        return other;
    }

    @Test
    @DisplayName("최대 5개까지만 등록할 수 있다")
    void limit() throws Exception {
        when(emergencyContactRepository.countByMember(ward)).thenReturn(5L);

        assertThatThrownBy(() -> wardContactService.addContact(EMAIL, request()))
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

        wardContactService.addContact(EMAIL, request());

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

        wardContactService.addContact(EMAIL, request());

        ArgumentCaptor<EmergencyContact> captor = ArgumentCaptor.forClass(EmergencyContact.class);
        verify(emergencyContactRepository).save(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo(1);
    }

    @Test
    @DisplayName("남의 피보호자 연락처는 수정할 수 없다")
    void cannotUpdateOthersContact() throws Exception {
        when(emergencyContactRepository.findById(5L)).thenReturn(Optional.of(
                EmergencyContact.builder().member(otherWard()).name("남")
                        .phone("01000000000").priority(1).build()));

        assertThatThrownBy(() -> wardContactService.updateContact(EMAIL, 5L, request()))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTACT_ACCESS_DENIED);
    }

    @Test
    @DisplayName("남의 피보호자 연락처는 삭제할 수 없다")
    void cannotDeleteOthersContact() {
        when(emergencyContactRepository.findById(5L)).thenReturn(Optional.of(
                EmergencyContact.builder().member(otherWard()).name("남")
                        .phone("01000000000").priority(1).build()));

        assertThatThrownBy(() -> wardContactService.deleteContact(EMAIL, 5L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTACT_ACCESS_DENIED);
        verify(emergencyContactRepository, never()).delete(any());
    }

    @Test
    @DisplayName("없는 연락처는 CONTACT_NOT_FOUND")
    void contactNotFound() {
        when(emergencyContactRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wardContactService.deleteContact(EMAIL, 404L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTACT_NOT_FOUND);
    }

    @Test
    @DisplayName("내 피보호자의 연락처는 수정된다 (전화번호는 숫자만)")
    void updatesOwnContact() throws Exception {
        when(emergencyContactRepository.findById(5L)).thenReturn(Optional.of(
                EmergencyContact.builder().member(ward).name("옛이름")
                        .phone("01000000000").priority(1).build()));

        wardContactService.updateContact(EMAIL, 5L, request());

        verify(emergencyContactRepository).findById(5L);
    }
}
