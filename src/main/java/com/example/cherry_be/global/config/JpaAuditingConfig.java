package com.example.cherry_be.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * JPA Auditing 설정.
 *
 * Application 이 아닌 별도 설정 클래스에 둔 이유:
 *  - @SpringBootApplication 에 붙이면 @WebMvcTest 등 슬라이스 테스트에서도
 *    Auditing 이 함께 로딩되어 불필요한 의존이 생긴다.
 *  - 테스트에서 이 설정만 교체하기도 쉽다.
 *
 * dateTimeProvider 는 Clock 을 통해 시각을 공급하므로,
 * 테스트에서 고정 Clock 빈을 등록하면 생성·수정 시각을 고정할 수 있다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
