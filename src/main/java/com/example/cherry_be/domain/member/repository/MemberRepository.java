package com.example.cherry_be.domain.member.repository;

import com.example.cherry_be.domain.member.entity.Member;
import com.example.cherry_be.domain.member.entity.MemberStatus;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
     * 끊긴 것으로 판정되고 아직 알리지 않은 피보호자의 id.
     *
     * deviceLastSeen 이 null 인 경우는 "한 번도 신호를 받은 적 없음"(연결 대기 중)이라
     * 끊김이 아니므로 제외한다. 등록만 해두고 아직 기기를 안 켠 상태까지
     * 끊김으로 알리면 첫 설치마다 오탐이 난다.
     *
     * 엔티티가 아니라 id 만 가져온다. 이 결과는 후보일 뿐이고 실제 발송 여부는
     * claimOfflineNotification 이 행 단위로 다시 판정하므로, 여기서 읽은 엔티티를
     * 들고 있어 봐야 그 사이 들어온 수신으로 낡은 값이 된다.
     */
    @Query("""
            select m.id from Member m
            where m.deviceLastSeen is not null
              and m.deviceLastSeen < :deadline
              and m.offlineNotified = false
            """)
    List<Long> findNewlyOfflineIds(@Param("deadline") LocalDateTime deadline);

    /**
     * 끊김 알림을 보낼 권리를 원자적으로 확보한다. 확보했으면 1, 아니면 0.
     *
     * 조회와 갱신을 따로 하면 그 사이가 비어 두 가지가 샌다.
     *  - 서버가 여러 대면 둘 다 offline_notified=false 를 읽고 각자 알림을 만든다 (중복)
     *  - 조회 직후 하트비트가 들어와도 이미 읽어 둔 낡은 값으로 알림을 보낸다 (오탐)
     *
     * 두 조건을 UPDATE 의 WHERE 에 함께 넣어 DB 가 행을 잠근 상태에서 판정하게 한다.
     * 먼저 커밋한 쪽만 1을 받고, 나머지는 0을 받아 아무것도 하지 않는다.
     *
     * 엔티티 더티체킹을 쓰지 않는 이유이기도 하다. Member 는 @DynamicUpdate 가 없어
     * 전체 컬럼 UPDATE 가 나가는데, 조회 시점 스냅샷으로 커밋하면 그 사이 기기가 복구되며
     * 갱신한 status·센서·배터리·device_last_seen 까지 통째로 되돌려 버린다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Member m
               set m.offlineNotified = true
             where m.id = :memberId
               and m.deviceLastSeen < :deadline
               and m.offlineNotified = false
            """)
    int claimOfflineNotification(@Param("memberId") Long memberId,
                                 @Param("deadline") LocalDateTime deadline);
}
