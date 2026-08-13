package com.example.cherry_be.domain.notification.repository;

import com.example.cherry_be.domain.notification.entity.Notification;
import com.example.cherry_be.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 수신자별 목록 (최신순)
    Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    // 안 읽은 알림 개수 (헤더 뱃지용)
    long countByUserAndIsReadFalse(User user);

    // 단건 조회 시 소유자까지 함께 확인해 타인의 알림 접근을 차단
    Optional<Notification> findByIdAndUser(Long id, User user);

    /**
     * 전체 읽음 처리.
     * 건별로 조회해 수정하면 N번의 UPDATE 가 발생하므로 벌크 연산으로 한 번에 처리한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user = :user AND n.isRead = false")
    int markAllAsReadByUser(@Param("user") User user);
}
