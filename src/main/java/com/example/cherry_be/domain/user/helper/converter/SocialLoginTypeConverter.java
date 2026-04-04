package com.example.cherry_be.domain.user.helper.converter;

import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import org.springframework.core.convert.converter.Converter; // 1. 반드시 이 경로여야 합니다!
import org.springframework.stereotype.Component; // 2. Configuration 대신 Component 추천

@Component
public class SocialLoginTypeConverter implements Converter<String, SocialLoginType> {

    @Override
    public SocialLoginType convert(String s) {
        // "google" -> "GOOGLE" -> SocialLoginType.GOOGLE로 변환
        try {
            return SocialLoginType.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 없는 타입이 들어왔을 때의 예외 처리
            return null;
        }
    }
}
