package com.example.cherry_be.domain.organization.service;

import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.organization.repository.OrganizationRepository;
import com.example.cherry_be.global.auth.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor // final이 붙은 필드를 자동으로 연결(주입)해 줍니다.
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder; // 비밀번호 암호화 도구
    private final JwtUtil jwtUtil;

    /**
     * 기관 회원가입 (계정 생성) 로직
     */
    @Transactional
    public Long signUp(String orgId, String rawPassword, String name) {
        // 1. 아이디 중복 검사
        if (organizationRepository.existsByOrgId(orgId)) {
            throw new IllegalArgumentException("이미 사용 중인 로그인 ID 입니다.");
        }

        // 2. 비밀번호 암호화 (스프링 시큐리티 필수)
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // 3. 기관번호 임의 부여 (예: ORG-1234abcd 형식)
        String generatedOrganizeId = "ORG-" + UUID.randomUUID().toString().substring(0, 8);

        // 4. 엔티티 생성
        Organization organization = Organization.builder()
                .orgId(orgId)
                .password(encodedPassword)
                .name(name)
                .orgCode(generatedOrganizeId)
                .build();

        // 5. DB에 저장 후, 생성된 고유 ID 반환
        return organizationRepository.save(organization).getId();
    }


    /**
     * 기관 로그인 및 JWT 토큰 발급
     */
    @Transactional(readOnly = true) // 데이터를 읽기만 하므로 성능 최적화를 위해 readOnly 적용
    public String login(String orgId, String rawPassword) {

        // 1. DB에서 아이디 조회 (없으면 예외 발생)
        Organization organization = organizationRepository.findByOrgId(orgId)
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 아이디입니다."));

        // 2. 비밀번호 검증 (입력한 비번과 DB의 암호화된 비번 비교)
        // passwordEncoder.matches() 가 내부적으로 안전하게 비교해 줍니다.
        if (!passwordEncoder.matches(rawPassword, organization.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 3. 로그인 성공! JwtUtil 기계를 작동시켜 토큰 발급
        // 기관(관리자)이므로 권한(role)은 "ROLE_ADMIN"으로 고정하여 발급합니다.
        return jwtUtil.createToken(organization.getOrgId(), "ROLE_ADMIN");
    }

}