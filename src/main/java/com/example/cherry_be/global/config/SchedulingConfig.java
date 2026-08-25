package com.example.cherry_be.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 활성화.
 *
 * AsyncConfig 와 분리해 둔다 — 스케줄러는 단일 스레드로 주기 실행되고
 * @Async 는 요청 중 곁가지 작업을 넘기는 용도라, 스레드 풀 설정이 서로 얽히면 곤란하다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
