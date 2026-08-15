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

    /**
     * 목록 조회 시 member 를 함께 가져온다.
     * 응답에 피보호자 이름이 들어가는데, LAZY 프록시를 그대로 두면
     * 알림 건수만큼 member_info 조회 쿼리가 추가로 나간다(N+1).
     * log 는 id 만 사용하므로 프록시가 초기화되지 않아 fetch 대상에서 제외한다.
     */
    @Query(value = "SELECT n FROM Notification n JOIN FETCH n.member "
                 + "WHERE n.user = :user ORDER BY n.createdAt DESC",
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.user = :user")
    Page<Notification> findByUserOrderByCreatedAtDesc(@Param("user") User user, Pageable pageable);

    long countByUserAndIsReadFalse(User user);

    // 단건 조회 시 수신자까지 함께 확인해 타인의 알림 접근을 차단
    Optional<Notification> findByIdAndUser(Long id, User user);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user = :user AND n.isRead = false")
    int markAllAsReadByUser(@Param("user") User user);

    // ── 기관(Organization) 수신함 ────────────────────

    @Query(value = "SELECT n FROM Notification n JOIN FETCH n.member "
                 + "WHERE n.organization = :organization ORDER BY n.createdAt DESC",
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.organization = :organization")
    Page<Notification> findByOrganizationOrderByCreatedAtDesc(
            @Param("organization") Organization organization, Pageable pageable);

    long countByOrganizationAndIsReadFalse(Organization organization);

    Optional<Notification> findByIdAndOrganization(Long id, Organization organization);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.organization = :organization AND n.isRead = false")
    int markAllAsReadByOrganization(@Param("organization") Organization organization);
}
