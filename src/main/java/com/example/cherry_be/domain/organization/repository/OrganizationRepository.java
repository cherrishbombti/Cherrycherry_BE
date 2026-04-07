package com.example.cherry_be.domain.organization.repository;

import com.example.cherry_be.domain.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    // 💡 자체 로그인을 구현할 때 사용자가 입력한 아이디(loginId)로 계정을 찾기 위한 메서드
    Optional<Organization> findByOrgId(String orgId);

    // 나중에 임의로 부여된 기관번호(organizeId)로 기관을 찾아야 할 때 쓸 수 있는 메서드 (미리 만들어둠)
    Optional<Organization> findByOrgCode(String orgCode);

    // 기관 로그인 ID가 이미 존재하는지(중복 가입 방지) 확인할 때 사용
    boolean existsByOrgId(String orgId);
}
