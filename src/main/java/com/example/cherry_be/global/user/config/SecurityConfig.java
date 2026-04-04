package com.example.cherry_be.global.user.config; // 패키지 경로 확인
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http

                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/login/**").permitAll() // 소셜 로그인 관련 경로는 모두 허용
                        .anyRequest().authenticated() // 나머지는 인증 필요
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                );
                /*
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login") // 소셜 로그인도 같은 로그인 페이지 사용
                );
                */

        return http.build();
    }
}