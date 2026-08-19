package com.example.cherry_be.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리 설정.
 *
 * 푸시 발송은 외부(FCM) 호출이라 응답이 늦어질 수 있다.
 * 이를 기기 데이터 수신(/api/device/data) 흐름 안에서 동기로 처리하면
 * 낙상 데이터 수신 자체가 지연된다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "pushTaskExecutor")
    public Executor pushTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("push-");
        executor.initialize();
        return executor;
    }
}
