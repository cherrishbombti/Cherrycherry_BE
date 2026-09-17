package com.example.cherry_be.domain.member.repository;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.entity.MemberStatus;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByDeviceMac(String deviceMac);
    List<Member> findByOrganization(Organization organization);
    List<Member> findByOrganizationAndStatus(Organization organization, MemberStatus status);
    Optional<Member> findByUser(User user); // 보호자로 피보호자 찾기

    /**
     * 방금 끊긴 것으로 판정되는 피보호자. (끊김 알림을 아직 보내지 않은 건만)
     *
     * deviceLastSeen 이 null 인 경우는 "한 번도 신호를 받은 적 없음"(연결 대기 중)이라
     * 끊김이 아니므로 제외한다. 등록만 해두고 기기를 아직 안 켠 상태까지
     * 끊김으로 알리면 첫 설치마다 오탐이 난다.
     *
     * 5초마다 도는 조회라 수신자(user·organization)를 함께 가져온다.
     * 알림 생성에서 곧바로 쓰는 값이어서 LAZY 로 두면 대상 수만큼 추가 조회가 발생한다.
     */
    @Query("""
            select m from Member m
            left join fetch m.user
            left join fetch m.organization
            where m.deviceLastSeen is not null
              and m.deviceLastSeen < :deadline
              and m.offlineNotified = false
            """)
    List<Member> findNewlyOffline(@Param("deadline") LocalDateTime deadline);
}
