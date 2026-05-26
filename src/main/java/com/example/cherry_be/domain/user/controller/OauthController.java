package com.example.cherry_be.domain.user.controller;

import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.user.dto.UserDto;
import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import com.example.cherry_be.domain.user.service.OauthService;
import com.example.cherry_be.domain.user.service.UserService;
import com.example.cherry_be.domain.user.service.UserService.LoginResult;
import com.example.cherry_be.global.auth.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@CrossOrigin
@RequiredArgsConstructor
@RequestMapping(value = "/api/auth")
@Slf4j
public class OauthController {

    private final OauthService oauthService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final MemberRepository memberRepository;

    @Value("${frontend.redirect-url}")
    private String frontendRedirectUrl;

    /**
     * [GET] 소셜 로그인 창으로 리다이렉트
     */
    @GetMapping(value = "/{socialLoginType}")
    public void socialLoginType(
            @PathVariable(name = "socialLoginType") SocialLoginType socialLoginType,
            HttpServletResponse response) throws IOException {

        log.info(">> 사용자로부터 SNS 로그인 요청을 받음 :: {} Social Login", socialLoginType);
        String redirectUrl = oauthService.authorize(socialLoginType);
        response.sendRedirect(redirectUrl);
    }

    /**
     * [GET] 소셜 로그인 완료 후 콜백 처리
     * isNewUser = 피보호자(ward) 미등록 여부
     *   true  → 피보호자 등록 화면(/guardian/signup)
     *   false → 홈 화면(/guardian/home)
     */
    @GetMapping(value = "/{socialLoginType}/callback")
    public void callback(
            @PathVariable(name = "socialLoginType") SocialLoginType socialLoginType,
            @RequestParam(name = "code") String code,
            HttpServletResponse response) throws IOException {

        log.info(">> 소셜 로그인 API 서버로부터 받은 code :: {}", code);

        String accessToken = oauthService.requestAccessToken(socialLoginType, code);
        log.info(">> [2] Access Token 획득 성공");

        UserDto userInfo = oauthService.getUserInfo(socialLoginType, accessToken);
        log.info(">> [3] 유저 정보 획득 성공 :: 이메일 = {}", userInfo.getOauthEmail());

        LoginResult result = userService.loginOrSignup(userInfo);
        log.info(">> [4] DB 저장/조회 완료 :: 회원 이름 = {}", result.getUser().getName());

        boolean hasWard = memberRepository.findByUser(result.getUser()).isPresent();
        boolean needsWardRegistration = !hasWard;
        log.info(">> [5] 피보호자 등록 여부 = {}, 등록 필요 = {}", hasWard, needsWardRegistration);

        String jwtToken = jwtUtil.createToken(result.getUser().getOauthEmail(), "ROLE_USER");
        log.info(">> [6] JWT 토큰 발급 완료");

        String redirectUrl = frontendRedirectUrl
                + "?token=" + jwtToken
                + "&isNewUser=" + needsWardRegistration;

        response.sendRedirect(redirectUrl);
    }

    /**
     * [POST] 프론트엔드에서 코드를 직접 서버로 보낼 때 사용 (JSON 응답)
     */
    @PostMapping(value = "/login")
    public ResponseEntity<?> socialLoginPost(@RequestBody Map<String, String> requestBody) {
        log.info(">> 리액트로부터 POST 로그인 요청 받음");

        String code = requestBody.get("code");
        String provider = requestBody.getOrDefault("provider", "google");
        SocialLoginType socialLoginType = SocialLoginType.valueOf(provider.toUpperCase());

        String accessToken = oauthService.requestAccessToken(socialLoginType, code);
        UserDto userInfo = oauthService.getUserInfo(socialLoginType, accessToken);
        LoginResult result = userService.loginOrSignup(userInfo);

        boolean hasWard = memberRepository.findByUser(result.getUser()).isPresent();

        String jwtToken = jwtUtil.createToken(result.getUser().getOauthEmail(), "ROLE_USER");

        return ResponseEntity.ok()
                .header("Authorization", "Bearer " + jwtToken)
                .body(Map.of(
                        "token", jwtToken,
                        "isNewUser", !hasWard,
                        "message", result.getUser().getName() + "님, 로그인을 환영합니다!"
                ));
    }
}
