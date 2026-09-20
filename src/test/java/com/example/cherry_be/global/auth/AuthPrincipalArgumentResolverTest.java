package com.example.cherry_be.global.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GuardianEmail·OrgId 가 실제 요청에서 채워지는지 확인한다.
 *
 * 리졸버 등록은 스프링이 하는 일이라 단위 테스트로는 확인되지 않는다. 등록이 빠지면
 * 스프링이 이 타입을 요청 본문이나 모델 속성으로 오해해 엉뚱한 실패를 내므로,
 * 실제 요청을 태워 한 번은 확인해 둔다.
 *
 * addFilters = false 로 시큐리티 필터를 끄고 principal 을 직접 넣는다.
 * 여기서 볼 것은 인증 자체가 아니라 "인증된 주체가 컨트롤러 파라미터까지 도달하는가" 다.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthPrincipalArgumentResolverTest {

    @Autowired private MockMvc mockMvc;

    private Authentication principal(String name, String role) {
        return new UsernamePasswordAuthenticationToken(
                name, null, List.of(new SimpleGrantedAuthority(role)));
    }

    @Test
    @DisplayName("보호자 엔드포인트에 GuardianEmail 이 채워진다")
    void resolvesGuardianEmail() throws Exception {
        // 값이 전달됐다면 서비스가 그 이메일로 계정을 찾다가 U001 로 끝난다.
        // (테스트 DB 는 비어 있다) 리졸버가 동작하지 않으면 여기까지 오지 못한다.
        mockMvc.perform(get("/api/wards/me/summary")
                        .principal(principal("guardian@example.com", "ROLE_USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("기관 엔드포인트에 OrgId 가 채워진다")
    void resolvesOrgId() throws Exception {
        mockMvc.perform(get("/api/targets")
                        .principal(principal("org01", "ROLE_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("O001"));
    }

    @Test
    @DisplayName("인증 주체 없이 도달하면 설정 오류로 보고 5xx 를 낸다")
    void rejectsMissingPrincipal() throws Exception {
        // 실제로는 SecurityConfig 가 먼저 401 로 막는다. 여기 오는 것은
        // 보호 대상 경로가 설정에서 누락됐다는 뜻이라 사용자 잘못이 아니다.
        mockMvc.perform(get("/api/wards/me/summary"))
                .andExpect(status().isInternalServerError());
    }
}
