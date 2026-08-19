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

    // 로그아웃 시 해당 기기 토큰 제거
    void deleteByToken(String token);
}
