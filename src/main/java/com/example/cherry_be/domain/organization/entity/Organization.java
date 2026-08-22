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

    // 기관번호 — 보호자가 앱에서 입력해 피보호자를 이 기관과 연동할 때 쓴다.
    // 가입 시 자동 부여된다. 이 번호로 기관을 특정하므로 중복되면 안 된다.
    // 자동 부여 전에 만들어진 계정이 있을 수 있어 nullable 은 유지한다.
    @Column(name = "org_code", unique = true)
    private Long orgCode;

    @Builder
    public Organization(String orgId, String password, String name, Long orgCode) {
        this.orgId = orgId;
        this.password = password;
        this.name = name;
        this.orgCode = orgCode;
    }
}
