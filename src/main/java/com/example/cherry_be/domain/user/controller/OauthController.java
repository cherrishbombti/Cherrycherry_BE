package com.example.cherry_be.domain.user.controller;

import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import com.example.cherry_be.domain.user.service.OauthService;
import com.example.cherry_be.domain.user.service.UserService; // 1. 매니저(UserService) 불러오기
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin
@RequiredArgsConstructor
@RequestMapping(value = "/auth")
@Slf4j
public class OauthController {

    private final OauthService oauthService;
    private final UserService userService; // 2. UserService 주입받기

    @GetMapping(value = "/{socialLoginType}")
    public void socialLoginType(@PathVariable(name = "socialLoginType") SocialLoginType socialLoginType) {
        log.info(">> 사용자로부터 SNS 로그인 요청을 받음 :: {} Social Login", socialLoginType);
        oauthService.authorize(socialLoginType);
    }

    @GetMapping(value = "/{socialLoginType}/callback")
    public String callback(@PathVariable(name = "socialLoginType") SocialLoginType socialLoginType,
                           @RequestParam(name = "code") String code) {
        log.info(">> 소셜 로그인 API 서버로부터 받은 code :: {}", code);

        // [Step 1] OauthService를 통해 '이름'과 '이메일' 정보를 가져옵니다.
        // (아직 OauthService에 이 메서드를 만들지 않았으므로 지금은 빨간 줄이 뜰 거예요!)
        // OauthUserInfo userInfo = oauthService.getUserInfo(socialLoginType, code);

        // [Step 2] 가져온 정보로 우리 서비스의 회원인지 확인하고 처리합니다 (UserService 호출)
        // User user = userService.loginOrSignup(userInfo.getEmail(), userInfo.getName(), socialLoginType);

        // [Step 3] 최종적으로 환영 메시지나 로그 결과를 보여줍니다.
        // return user.getName() + "님, 로그인을 환영합니다! (이메일: " + user.getOauthEmail() + ")";

        return "로그인 성공! 코드는: " + code + " (이제 이 코드로 유저 정보를 가져올 차례입니다)";
    }
}