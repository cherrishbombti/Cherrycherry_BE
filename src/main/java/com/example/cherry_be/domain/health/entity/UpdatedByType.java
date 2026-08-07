package com.example.cherry_be.domain.health.entity;

/**
 * 건강정보를 마지막으로 수정한 주체.
 * 보호자(users)와 기관(organization) 중 어느 쪽인지 단일 FK로 표현할 수 없어 타입+ID로 나눠 저장한다.
 */
public enum UpdatedByType {
    USER,          // 보호자(가족)
    ORGANIZATION   // 기관(사회복지사)
}
