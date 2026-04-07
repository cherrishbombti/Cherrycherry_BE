package com.example.cherry_be.domain.organization.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 기본 생성자 접근 제어로 안전성 확보
@Table(name = "organization")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // BIGINT, PK (자동 증가)
    private Long id;

    @Column(name = "org_id", nullable = false, unique = true) // 기관 로그인 ID (중복 불가)
    private String orgId;

    @Column(nullable = false) // 비밀번호
    private String password;

    @Column(nullable = false) // 기관명
    private String name;

    @Column(name = "org_code") // 기관번호 (임의 부여 예정이므로 nullable 허용)
    private String orgCode;

    @Builder
    public Organization(String orgId, String password, String name, String orgCode) {
        this.orgId = orgId;
        this.password = password;
        this.name = name;
        this.orgCode = orgCode;
    }
}
