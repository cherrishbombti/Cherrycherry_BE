package com.example.cherry_be.domain.notification.repository;

import com.example.cherry_be.domain.notification.entity.Notification;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // ── 보호자(User) 수신함 ──────────────────────────
    Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    long countByUserAndIsReadFalse(User user);

    // 단건 조회 시 수신자까지 함께 확인해 타인의 알림 접근을 차단
    Optional<Notification> findByIdAndUser(Long id, User user);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user = :user AND n.isRead = false")
    int markAllAsReadByUser(@Param("user") User user);

    // ── 기관(Organization) 수신함 ────────────────────
    Page<Notification> findByOrganizationOrderByCreatedAtDesc(Organization organization, Pageable pageable);

    long countByOrganizationAndIsReadFalse(Organization organization);

    Optional<Notification> findByIdAndOrganization(Long id, Organization organization);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.organization = :organization AND n.isRead = false")
    int markAllAsReadByOrganization(@Param("organization") Organization organization);
}
