package com.example.cherry_be.domain.user.service.social;

import com.example.cherry_be.domain.user.dto.UserDto;
import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import com.example.cherry_be.domain.user.service.social.SocialOauth;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;


import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GoogleOauth implements SocialOauth {
    private final ObjectMapper objectMapper;
    private final ClientRegistrationRepository clientRegistrationRepository;

    @Override
    public String getOauthRedirectURL() {
        ClientRegistration google = clientRegistrationRepository.findByRegistrationId("google");
        return google.getProviderDetails().getAuthorizationUri()
                + "?client_id=" + google.getClientId()
                + "&redirect_uri=" + google.getRedirectUri()
                + "&response_type=code"
                + "&scope=" + String.join(" ", google.getScopes());
    }

    @Override
    public String requestAccessToken(String code) {
        ClientRegistration google = clientRegistrationRepository.findByRegistrationId("google");
        RestTemplate restTemplate = new RestTemplate();

        Map<String, String> params = new HashMap<>();
        params.put("code", code);
        params.put("client_id", google.getClientId());
        params.put("client_secret", google.getClientSecret());
        params.put("redirect_uri", google.getRedirectUri());
        params.put("grant_type", "authorization_code");

        ResponseEntity<String> responseEntity = restTemplate.postForEntity(google.getProviderDetails().getTokenUri(), params, String.class);

        return (responseEntity.getStatusCode() == HttpStatus.OK) ? responseEntity.getBody() : "구글 토큰 요청 실패";
    }

    @Override
    public String getUserInfo(String accessToken) {
        ClientRegistration google = clientRegistrationRepository.findByRegistrationId("google");
        RestTemplate restTemplate = new RestTemplate();

        // 1. 헤더 설정: Bearer 토큰 주입
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 2. 유저 정보 API 호출 (GET)
        ResponseEntity<String> response = restTemplate.exchange(
                google.getProviderDetails().getUserInfoEndpoint().getUri(),
                HttpMethod.GET,
                entity,
                String.class
        );

        return (response.getStatusCode() == HttpStatus.OK) ? response.getBody() : "구글 유저 정보 요청 실패";
    }

    @Override
    public SocialLoginType type() {
        return SocialLoginType.GOOGLE;
    }

    public UserDto parseUserInfo(String userInfoJson) throws JsonProcessingException {
        JsonNode jsonNode = objectMapper.readTree(userInfoJson);

        return UserDto.builder()
                .oauthProvider("GOOGLE")
                .oauthEmail(jsonNode.get("email").asText())
                .name(jsonNode.get("name").asText())
                .cellNum(null) // 구글은 기본적으로 전화번호를 주지 않음
                .build();
    }
}