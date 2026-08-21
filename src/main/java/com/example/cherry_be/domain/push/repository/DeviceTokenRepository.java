package com.example.cherry_be.domain.push.repository;

import com.example.cherry_be.domain.push.entity.DeviceToken;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    // 발송 시 수신자의 모든 기기를 조회
    List<DeviceToken> findByUser(User user);

    List<DeviceToken> findByOrganization(Organization organization);

    // 비동기 발송에서는 엔티티 대신 ID 로 조회한다 (트랜잭션 밖이라 프록시를 쓸 수 없음)
    List<DeviceToken> findByUserId(Long userId);

    List<DeviceToken> findByOrganizationId(Long organizationId);
}
