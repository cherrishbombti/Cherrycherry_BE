package com.example.cherry_be.domain.user.service.social;

import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;

public interface SocialOauth {
    String getOauthRedirectURL();
    String requestAccessToken(String code);
    String getUserInfo(String accessToken);
    // 하위 클래스(Google, Kakao)가 "나는 누구다"라고 직접 구현하게 합니다.
    SocialLoginType type();
}