package com.example.cherry_be.domain.organization.service;

import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.notification.dto.NotificationPageResponse;
import com.example.cherry_be.domain.notification.service.NotificationService;
import com.example.cherry_be.domain.organization.dto.OrgMeResponse;
import com.example.cherry_be.domain.organization.repository.OrganizationRepository;
import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import com.example.cherry_be.global.auth.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.ThreadLocalRandom;



@Service
@RequiredArgsConstructor // final이 붙은 필드를 자동으로 연결(주입)해 줍니다.
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder; // 비밀번호 암호화 도구
    private final JwtUtil jwtUtil;
    private final NotificationService notificationService;

    /** 로그인 ID로 기관 조회 (로그인 실패는 별도 코드를 쓰므로 여기서 처리하지 않는다) */
    private Organization findOrganization(String orgId) {
        return organizationRepository.findByOrgId(orgId)
                .orElseThrow(() -> new CustomException(ErrorCode.ORG_NOT_FOUND));
    }

    /**
     * 기관 회원가입 (계정 생성) 로직
     */
    @Transactional
    public Long signUp(String orgId, String rawPassword, String name) {
        // 1. 아이디 중복 검사
        if (organizationRepository.existsByOrgId(orgId)) {
            throw new CustomException(ErrorCode.ORG_ID_DUPLICATE);
        }

        // 2. 비밀번호 암호화 (스프링 시큐리티 필수)
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // 3. 기관번호 자동 부여 (보호자가 앱에서 입력할 번호)
        Long orgCode = generateUniqueOrgCode();

        // 4. 엔티티 생성
        Organization organization = Organization.builder()
                .orgId(orgId)
                .password(encodedPassword)
                .name(name)
                .orgCode(orgCode)
                .build();

        // 5. DB에 저장 후, 생성된 고유 ID 반환
        return organizationRepository.save(organization).getId();
    }

    // 기관번호 채번 재시도 횟수. 이 정도면 실패 확률이 사실상 0이다.
    private static final int ORG_CODE_MAX_ATTEMPTS = 10;

    /**
     * 중복되지 않는 8자리 기관번호를 만든다.
     *
     * 보호자는 이 번호 하나로 기관을 지정하므로 중복되면 엉뚱한 기관에 연동되거나
     * 조회 자체가 실패한다. 랜덤이라 언젠가는 겹칠 수 있어 확인 후 재시도한다.
     *
     * 6자리(90만 개)는 무차별 대입에 약하다는 리뷰 지적으로 8자리(9천만 개)로 늘렸다.
     * 시도 횟수 제한(WardOrgCodeAttemptLimiter)과 함께 방어한다.
     */
    private Long generateUniqueOrgCode() {
        for (int attempt = 0; attempt < ORG_CODE_MAX_ATTEMPTS; attempt++) {
            Long candidate = ThreadLocalRandom.current().nextLong(10000000L, 100000000L);
            if (organizationRepository.findByOrgCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new CustomException(ErrorCode.ORG_CODE_GENERATION_FAILED);
    }


    /**
     * 기관 로그인 및 JWT 토큰 발급
     */
    @Transactional(readOnly = true) // 데이터를 읽기만 하므로 성능 최적화를 위해 readOnly 적용
    public String login(String orgId, String rawPassword) {

        // 1. DB에서 아이디 조회 (없으면 예외 발생)
        Organization organization = organizationRepository.findByOrgId(orgId)
                .orElseThrow(() -> new CustomException(ErrorCode.LOGIN_FAILED));

        // 2. 비밀번호 검증 (입력한 비번과 DB의 암호화된 비번 비교)
        // passwordEncoder.matches() 가 내부적으로 안전하게 비교해 줍니다.
        if (!passwordEncoder.matches(rawPassword, organization.getPassword())) {
            throw new CustomException(ErrorCode.LOGIN_FAILED);
        }

        // 3. 로그인 성공! JwtUtil 기계를 작동시켜 토큰 발급
        // 기관(관리자)이므로 권한(role)은 "ROLE_ADMIN"으로 고정하여 발급합니다.
        return jwtUtil.createToken(organization.getOrgId(), "ROLE_ADMIN");
    }


    /**
     * 로그인한 기관(사회복지사) 본인 정보 조회
     * [GET] /api/org/me
     */
    @Transactional(readOnly = true)
    public OrgMeResponse getMyInfo(String orgId) {
        return OrgMeResponse.from(findOrganization(orgId));
    }


    // ── 알림함 (#25) ────────────────────────────────
    // 기관 계정 기준 수신함이라 특정 피보호자에 종속되지 않는다.

    @Transactional(readOnly = true)
    public NotificationPageResponse getNotifications(String orgId, Pageable pageable) {
        return notificationService.getNotifications(findOrganization(orgId), pageable);
    }

    @Transactional
    public void readNotification(String orgId, Long notificationId) {
        notificationService.markAsRead(findOrganization(orgId), notificationId);
    }

    @Transactional
    public int readAllNotifications(String orgId) {
        return notificationService.markAllAsRead(findOrganization(orgId));
    }

}
