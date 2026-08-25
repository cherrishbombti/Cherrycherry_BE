package com.example.cherry_be.global.auth;

import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 폐기 시각은 서비스가 KST 로 계산해 넘긴다.
    // CURRENT_TIMESTAMP 를 쓰면 JVM 이 아니라 DB 서버 타임존으로 평가되어,
    // 엔티티가 기록한 값과 다른 기준의 시각이 같은 컬럼에 섞인다.

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now "
            + "WHERE r.user = :user AND r.revokedAt IS NULL")
    int revokeAllByUser(@Param("user") User user, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now "
            + "WHERE r.organization = :organization AND r.revokedAt IS NULL")
    int revokeAllByOrganization(@Param("organization") Organization organization,
                                @Param("now") LocalDateTime now);

    /**
     * 더 이상 쓸 수 없게 된 지 오래된 토큰을 삭제한다.
     *
     * 만료·폐기 직후 바로 지우지 않고 보관 기간을 두는 이유는,
     * "왜 로그아웃됐나" 같은 문의를 추적할 근거를 남기기 위해서다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken r "
            + "WHERE r.expiresAt < :cutoff "
            + "   OR (r.revokedAt IS NOT NULL AND r.revokedAt < :cutoff)")
    int deleteDeadTokensBefore(@Param("cutoff") LocalDateTime cutoff);
}
