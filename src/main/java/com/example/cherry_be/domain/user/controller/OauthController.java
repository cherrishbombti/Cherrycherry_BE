    package com.example.cherry_be.domain.user.controller;

    import com.example.cherry_be.domain.user.dto.UserDto;
    import com.example.cherry_be.domain.user.entity.User;
    import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
    import com.example.cherry_be.domain.user.service.OauthService;
    import com.example.cherry_be.domain.user.service.UserService;
    import com.example.cherry_be.global.auth.JwtUtil; // 🔥 JwtUtil 경로 임포트 확인!
    import jakarta.servlet.http.HttpServletResponse;
    import lombok.RequiredArgsConstructor;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.http.ResponseEntity;
    import org.springframework.web.bind.annotation.*;

    import java.io.IOException;

    @RestController
    @CrossOrigin
    @RequiredArgsConstructor
    @RequestMapping(value = "/api/auth")
    @Slf4j
    public class OauthController {

        private final OauthService oauthService;
        private final UserService userService;
        private final JwtUtil jwtUtil; // 🔥 1. JwtUtil 주입받기

        @GetMapping(value = "/{socialLoginType}")
        public void socialLoginType(
                @PathVariable(name = "socialLoginType") SocialLoginType socialLoginType,
                HttpServletResponse response) throws IOException {

            log.info(">> 사용자로부터 SNS 로그인 요청을 받음 :: {} Social Login", socialLoginType);

            // 🔥 빨간 줄이 뜨던 곳을 기존에 쓰시던 authorize 메서드로 변경합니다.
            String redirectUrl = oauthService.authorize(socialLoginType);

            // 브라우저에게 "이 주소(카카오 로그인 화면)로 강제 이동해!" 라고 명령합니다.
            response.sendRedirect(redirectUrl);
        }

        // 🔥 2. 반환 타입을 String에서 ResponseEntity<?>로 변경
        @GetMapping(value = "/{socialLoginType}/callback")
        public ResponseEntity<?> callback(@PathVariable(name = "socialLoginType") SocialLoginType socialLoginType,
                                          @RequestParam(name = "code") String code) {
            log.info(">> 소셜 로그인 API 서버로부터 받은 code :: {}", code);

            // 1. 구글 Access Token 획득
            String accessToken = oauthService.requestAccessToken(socialLoginType, code);
            log.info(">> [2] 구글 Access Token 획득 성공");

            // 2. 구글에서 유저 정보 가져오기 (UserDto 반환)
            UserDto userInfo = oauthService.getUserInfo(socialLoginType, accessToken);
            log.info(">> [3] 구글 유저 정보 획득 성공 :: 이메일 = {}", userInfo.getOauthEmail());

            // 3. 우리 DB에 유저 저장 또는 조회 (작성하신 UserService 로직 사용!)
            User user = userService.loginOrSignup(userInfo);
            log.info(">> [4] DB 저장/조회 완료 :: 회원 이름 = {}", user.getName());

            // 4. 드디어 우리 서비스 전용 JWT 토큰 발급!
            // (주의: jwtUtil 내부의 토큰 생성 메서드 이름과 파라미터가 맞는지 확인해주세요)
            String jwtToken = jwtUtil.createToken(user.getOauthEmail(), "ROLE_USER");

            return ResponseEntity.ok()
                    .header("Authorization", "Bearer " + jwtToken)
                    .body("로그인 성공! 환영합니다 " + user.getName() + "님. (JWT 토큰이 헤더에 발급되었습니다)");
        }
    }