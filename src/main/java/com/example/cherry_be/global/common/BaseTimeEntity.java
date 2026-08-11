package com.example.cherry_be.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 생성·수정 시각을 자동으로 관리하는 공통 부모.
 *
 * 각 엔티티에서 LocalDateTime.now()를 직접 호출하면
 *  - 엔티티가 늘어날 때마다 같은 코드가 복제되고
 *  - 시각이 코드에 고정되어 테스트에서 제어할 수 없다.
 *
 * Auditing은 JPA 생명주기(@PrePersist/@PreUpdate)에 개입해 값을 채우므로
 * 엔티티가 스프링 빈이 아니어도 동작하며, DateTimeProvider 를 교체하면
 * 테스트에서 시각을 고정할 수 있다.
 *
 * updatedAt 은 Hibernate 가 실제 변경(dirty)을 감지했을 때만 갱신된다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
