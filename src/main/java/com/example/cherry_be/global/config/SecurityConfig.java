package com.example.cherry_be.global.config; // 패키지 경로 확인
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;


    // 1. 비밀번호 암호화 도구를 스프링 빈(Bean)으로 등록
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 2. 전체적인 보안 규칙 설정
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http

                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 리액트와 통신하는 REST API 서버이므로 CSRF 보호 비활성화
                .csrf(AbstractHttpConfigurer::disable)

                // 기본 폼 로그인과 HTTP Basic 인증 비활성화 (우리는 JWT를 쓸 것이기 때문)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // 세션을 생성하지 않음 (STATELESS 설정)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // API 주소별 접근 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // 로그인, 회원가입 관련 API는 모두에게 접근 허용
                        .requestMatchers("/api/org/signup", "/api/org/login").permitAll()
                        // 그 외의 모든 요청은 토큰 인증을 거쳐야 함
                        .anyRequest().authenticated()
                        .requestMatchers("/auth/**", "/login/**","/error", "/favicon.ico","/api/v1/**").permitAll() // 소셜 로그인 관련 경로는 모두 허용
                        .anyRequest().authenticated() // 나머지는 인증 필요
                )
                .oauth2Login(oauth2 -> oauth2
                        .defaultSuccessUrl("/", true)
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();


        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true); // 쿠키나 인증 정보 포함 허용

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 모든 경로("/**")에 대해 위에서 만든 규칙을 적용합니다.
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}

