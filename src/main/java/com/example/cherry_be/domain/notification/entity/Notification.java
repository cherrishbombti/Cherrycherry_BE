package com.example.cherry_be.domain.notification.entity;

import com.example.cherry_be.domain.log.entity.Log;
import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림 수신 이력 (알림함).
 *
 * fall_log 가 "무슨 일이 있었나"(사건)라면, 이 테이블은 "누구에게 알렸고 읽었나"(전달)를 기록한다.
 * 하나의 사건에 여러 수신자가 생길 수 있으므로 fall_log : notification = 1 : N.
 *
 * 전송 방식(PUSH/SMS)은 여기에 두지 않는다. 한 사건을 두 경로로 보내면 행이 2개가 되어
 * 알림함에 같은 내용이 중복 노출되고 is_read 도 갈라지기 때문이다.
 * 실제 발송 기능을 붙일 때 notification_delivery 를 별도로 만든다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notification")
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 누구에 관한 알림인가 (피보호자)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // 누구에게 보냈나 (수신자)
    // 기관이 등록한 피보호자는 연결된 보호자가 없을 수 있어 nullable 로 둔다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // 어떤 사건 때문인가. DEVICE_OFFLINE 처럼 대응 로그가 없는 알림도 있어 nullable.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "log_id")
    private Log log;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Builder
    public Notification(Member member, User user, Log log, NotificationType notificationType) {
        this.member = member;
        this.user = user;
        this.log = log;
        this.notificationType = notificationType;
        this.isRead = false;
    }

    /** 이미 읽은 알림을 다시 읽어도 상태가 바뀌지 않도록 한다. */
    public void markAsRead() {
        if (!this.isRead) {
            this.isRead = true;
        }
    }
}
