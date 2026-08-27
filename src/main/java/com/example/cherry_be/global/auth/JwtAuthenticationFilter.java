package com.example.cherry_be.global.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // 인증이 필요 없는 경로도 이 필터를 지나므로, 헤더가 없는 것은 정상 동작이다.
            // INFO 로 남기면 하루 수만 줄이 쌓여 진짜 문제를 덮는다.
            log.debug("JWT 필터: Authorization 헤더 없음 또는 형식 불일치 - {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        log.info("JWT 필터: 토큰 수신됨 - URI: {}", request.getRequestURI());

        if (jwtUtil.validateToken(token)) {
            String subject = jwtUtil.getSubject(token);
            String role = jwtUtil.getRole(token);
            log.info("JWT 필터: 검증 성공 - subject: {}, role: {}", subject, role);

            if (subject != null) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                subject,
                                null,
                                List.of(new SimpleGrantedAuthority(role))
                        );

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
                log.info("JWT 필터: SecurityContext 세팅 완료");
            }
        } else {
            log.warn("JWT 필터: 토큰 검증 실패 - URI: {}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
}
