package com.example.cherry_be.domain.health.repository;

import com.example.cherry_be.domain.health.entity.MemberHealth;
import com.example.cherry_be.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberHealthRepository extends JpaRepository<MemberHealth, Long> {

    Optional<MemberHealth> findByMember(Member member);

    /**
     * 엔티티를 읽지 않고 바로 지운다.
     *
     * 파생 삭제(deleteByMember)는 대상을 SELECT 해 엔티티로 만든 뒤 지우는데,
     * 그 과정에서 암호화 컬럼의 복호화가 일어난다. 복호화가 실패하는 행은
     * 그 방식으로는 지울 수 없으므로 벌크 삭제가 필요하다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM MemberHealth h WHERE h.member = :member")
    int deleteByMemberInBulk(@Param("member") Member member);
}
