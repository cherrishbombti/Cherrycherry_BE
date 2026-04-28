package com.example.cherry_be.domain.user.service.social;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.example.cherry_be.domain.user.dto.UserDto;
import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import com.example.cherry_be.domain.user.service.social.SocialOauth;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
        params.add("client_secret", kakao.getClientSecret());
        params.add("redirect_uri", kakao.getRedirectUri());
        params.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        ResponseEntity<String> responseEntity = restTemplate.postForEntity(kakao.getProviderDetails().getTokenUri(), request, String.class);

        return (responseEntity.getStatusCode() == HttpStatus.OK) ? responseEntity.getBody() : "카카오 토큰 요청 실패";
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

    public UserDto parseUserInfo(String userInfoJson) throws JsonProcessingException {
        JsonNode jsonNode = objectMapper.readTree(userInfoJson);
        JsonNode kakaoAccount = jsonNode.get("kakao_account");
        JsonNode profile = kakaoAccount.get("profile");

        return UserDto.builder()
                .oauthProvider("KAKAO")
                .oauthEmail(kakaoAccount.get("email").asText())
                .name(profile.get("nickname").asText())
                .cellNum(null) // 카카오는 별도 설정 없으면 전화번호 안 줌
                .build();
    }
}