package com.example.cherry_be.domain.notification.service;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.notification.entity.Notification;
import com.example.cherry_be.domain.notification.entity.NotificationType;
import com.example.cherry_be.domain.notification.event.NotificationCreatedEvent;
import com.example.cherry_be.domain.notification.repository.NotificationRepository;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 알림 생성.
 *
 * 수신자가 보호자와 기관 두 종류라 행을 따로 만든다. 한 행을 공유하면 is_read 도 공유되어
 * 한쪽이 읽으면 다른 쪽에서도 읽음으로 보이고, 실제로 알림을 놓치게 된다.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private NotificationService notificationService;

    private final User guardian = User.builder()
            .id(1L).oauthEmail("g@example.com").name("보호자")
            .oauthProvider(SocialLoginType.GOOGLE).build();

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, eventPublisher);
        lenient().when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> {
                    Notification n = inv.getArgument(0);
                    ReflectionTestUtils.setField(n, "id", 1L);
                    return n;
                });
    }

    private Organization org() {
        Organization organization = Organization.builder()
                .orgId("org01").name("복지관").password("x").build();
        ReflectionTestUtils.setField(organization, "id", 1L);
        return organization;
    }

    private Member member(User user, Organization organization) {
        Member m = Member.builder().user(user).organization(organization).name("김어르신").build();
        ReflectionTestUtils.setField(m, "id", 10L);
        return m;
    }

    @Test
    @DisplayName("보호자만 있으면 1건")
    void guardianOnly() {
        notificationService.create(member(guardian, null), null, NotificationType.FALL);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(guardian);
        assertThat(captor.getValue().getOrganization()).isNull();
    }

    @Test
    @DisplayName("기관만 있으면 1건")
    void organizationOnly() {
        Organization organization = org();

        notificationService.create(member(null, organization), null, NotificationType.FALL);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getOrganization()).isEqualTo(organization);
        assertThat(captor.getValue().getUser()).isNull();
    }

    @Test
    @DisplayName("보호자와 기관이 모두 연결돼 있으면 수신자별로 2건 — 읽음 표시를 공유하지 않기 위해")
    void bothReceiversGetOwnRow() {
        notificationService.create(member(guardian, org()), null, NotificationType.FALL);

        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(eventPublisher, times(2)).publishEvent(any(NotificationCreatedEvent.class));
    }

    @Test
    @DisplayName("수신자가 아무도 없으면 만들지 않는다")
    void noReceiver() {
        notificationService.create(member(null, null), null, NotificationType.FALL);

        verify(notificationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(NotificationCreatedEvent.class));
    }

    @Test
    @DisplayName("대응 로그가 없는 알림(DEVICE_OFFLINE)도 만들 수 있다")
    void allowsNullLog() {
        notificationService.create(member(guardian, null), null, NotificationType.DEVICE_OFFLINE);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getLog()).isNull();
        assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.DEVICE_OFFLINE);
    }

    @Test
    @DisplayName("발송은 저장 뒤에 이벤트로 넘긴다 (트랜잭션 커밋 후 발송을 위해)")
    void publishesEventAfterSave() {
        notificationService.create(member(guardian, null), null, NotificationType.FALL);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(notificationRepository, eventPublisher);
        order.verify(notificationRepository).save(any(Notification.class));
        order.verify(eventPublisher).publishEvent(any(NotificationCreatedEvent.class));
    }
}
