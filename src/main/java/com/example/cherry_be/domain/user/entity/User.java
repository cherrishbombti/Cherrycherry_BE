package com.example.cherry_be.domain.user.entity;

import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Getter
@Table(name = "users") // 'user'는 DB 예약어인 경우가 많아 'users'로 지정하는 게 안전해요
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String oauthEmail; // 소셜에서 받아온 고유 이메일 (로그인 ID 역할)

    @Column(nullable = false)
    private String name; // 사용자 이름

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider", nullable = false)
    private SocialLoginType oauthProvider; // GOOGLE, KAKAO 등

    @Column(name = "cell_num")
    private String cellNum;

}