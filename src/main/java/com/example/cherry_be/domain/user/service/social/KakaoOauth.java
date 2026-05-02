package com.example.cherry_be.domain.user.service.social;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.example.cherry_be.domain.user.dto.UserDto;
import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoOauth implements SocialOauth {

    private final ObjectMapper objectMapper;
    private final ClientRegistrationRepository clientRegistrationRepository;

    @Override
    public String getOauthRedirectURL() {
        ClientRegistration kakao = clientRegistrationRepository.findByRegistrationId("kakao");
        return kakao.getProviderDetails().getAuthorizationUri()
                + "?client_id=" + kakao.getClientId()
                + "&redirect_uri=" + kakao.getRedirectUri()
                + "&response_type=code";
    }

    @Override
    public String requestAccessToken(String code) {
        ClientRegistration kakao = clientRegistrationRepository.findByRegistrationId("kakao");
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", kakao.getClientId());
        params.add("redirect_uri", kakao.getRedirectUri());
        params.add("grant_type", "authorization_code");
        // 클라이언트 시크릿을 발급받아 쓰신다면 아래 주석을 해제하세요.
        params.add("client_secret", kakao.getClientSecret());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        ResponseEntity<String> responseEntity = restTemplate.postForEntity(kakao.getProviderDetails().getTokenUri(), request, String.class);

        if (responseEntity.getStatusCode() == HttpStatus.OK) {
            try {
                // JSON 덩어리에서 "access_token"만 쏙 빼냅니다.
                JsonNode jsonNode = objectMapper.readTree(responseEntity.getBody());
                return jsonNode.get("access_token").asText();
            } catch (Exception e) {
                log.error("카카오 엑세스 토큰 파싱 에러", e);
                throw new RuntimeException("카카오 엑세스 토큰 파싱 실패");
            }
        }
        return "카카오 토큰 요청 실패";
    }

    @Override
    public String getUserInfo(String accessToken) {
        ClientRegistration kakao = clientRegistrationRepository.findByRegistrationId("kakao");
        RestTemplate restTemplate = new RestTemplate();

        // 1. 헤더 설정: Bearer 토큰 주입
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 2. 유저 정보 API 호출 (GET)
        ResponseEntity<String> response = restTemplate.exchange(
                kakao.getProviderDetails().getUserInfoEndpoint().getUri(),
                HttpMethod.GET,
                entity,
                String.class
        );

        return (response.getStatusCode() == HttpStatus.OK) ? response.getBody() : "카카오 유저 정보 요청 실패";
    }

    @Override
    public SocialLoginType type() {
        return SocialLoginType.KAKAO;
    }

    // 🔥 여기서부터가 핵심 방어 코드가 적용된 부분입니다!
    public UserDto parseUserInfo(String userInfoJson) throws JsonProcessingException {
        JsonNode jsonNode = objectMapper.readTree(userInfoJson);

        // 1. get() 대신 path()를 사용하여 값이 없어도 NullPointerException이 터지지 않게 방어!
        JsonNode kakaoAccount = jsonNode.path("kakao_account");
        JsonNode profile = kakaoAccount.path("profile");

        // 2. 안전하게 값 꺼내기 (만약 사용자가 동의를 안 해서 값이 없다면, 소괄호 안의 기본값이 들어갑니다)
        String email = kakaoAccount.path("email").asText("이메일동의안함@kakao.com");
        String nickname = profile.path("nickname").asText("이름없음");

        log.info(">> 카카오 유저 파싱 안전하게 완료! 이름: {}, 이메일: {}", nickname, email);

        return UserDto.builder()
                .oauthProvider("KAKAO")
                .oauthEmail(email)
                .name(nickname)
                //.cellNum(null) // 카카오는 별도 설정 없으면 전화번호 안 줌
                .build();
    }
}