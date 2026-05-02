package com.example.cherry_be.domain.user.service;

import com.example.cherry_be.domain.user.dto.UserDto;
import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import com.example.cherry_be.domain.user.service.social.SocialOauth; // 1. SocialOauth 위치 임포트
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List; // 2. List 임포트 (이게 없으면 List에 빨간 줄 뜹니다)

@Service
@RequiredArgsConstructor


public class OauthService {

    // 3. 주입받을 인터페이스 리스트
    private final List<SocialOauth> socialOauthList;
    private final HttpServletResponse response;


    // 반환 타입을 void에서 String으로 변경!
    public String authorize(SocialLoginType socialLoginType) {
        SocialOauth socialOauth = this.findSocialOauthByType(socialLoginType);

        // 복잡한 redirect 로직 싹 빼고, 깔끔하게 주소만 만들어서 리턴!
        return socialOauth.getOauthRedirectURL();
    }


    public String requestAccessToken(SocialLoginType socialLoginType, String code) {
        // 1. 어떤 방식(Google/Kakao)으로 로그인을 시도했는지, 구글이 준 'Code'는 뭔지 확인
        System.out.println("==== [OAuth Step 1] 요청 타입: " + socialLoginType);
        System.out.println("==== [OAuth Step 1] 구글이 준 인증 코드(Code): " + code);

        SocialOauth socialOauth = this.findSocialOauthByType(socialLoginType);

        // 2. 구글에게 'Code'를 주고 'AccessToken'을 받아옴 (서버 대 서버 대화)
        String accessToken = socialOauth.requestAccessToken(code);

        // 3. 🌟 여기가 핵심! 엑세스 토큰이 찍히면 구글 신원 확인 성공입니다.
        System.out.println("==== [OAuth Step 2] 구글에서 받아온 엑세스 토큰: " + accessToken);

        return accessToken;
    }

    private SocialOauth findSocialOauthByType(SocialLoginType socialLoginType) {
        return socialOauthList.stream()
                .filter(x -> x.type() == socialLoginType)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 SocialLoginType 입니다."));
    }

    public UserDto getUserInfo(SocialLoginType socialLoginType, String accessToken) {
        SocialOauth socialOauth = this.findSocialOauthByType(socialLoginType);

        // 1. 구글/카카오에 엑세스 토큰을 보내서 유저 정보(JSON 문자열)를 받아옴
        String userInfoJson = socialOauth.getUserInfo(accessToken);

        try {
            // 2. 받아온 JSON을 우리가 쓰기 편한 UserDto로 파싱해서 반환
            return socialOauth.parseUserInfo(userInfoJson);
        } catch (Exception e) {
            throw new RuntimeException("유저 정보 파싱 중 오류가 발생했습니다.", e);
        }
    }

}

