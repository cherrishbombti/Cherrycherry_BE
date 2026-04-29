package com.example.cherry_be.domain.user.service.social;

import com.example.cherry_be.domain.user.dto.UserDto;
import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import com.example.cherry_be.domain.user.service.social.SocialOauth;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleOauth implements SocialOauth {
    //private final ObjectMapper objectMapper;
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

        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> params = new HashMap<>();
        params.put("code", code);
        params.put("client_id", google.getClientId());
        params.put("client_secret", google.getClientSecret());
        params.put("redirect_uri", google.getRedirectUri());
        params.put("grant_type", "authorization_code");

        ResponseEntity<String> responseEntity = restTemplate.postForEntity(google.getProviderDetails().getTokenUri(), params, String.class);
        if (responseEntity.getStatusCode() == HttpStatus.OK) {
            try {
                // 🔥 통째로 받은 JSON 문자열을 파싱합니다.
                JsonNode jsonNode = objectMapper.readTree(responseEntity.getBody());
                // 🔥 JSON 구조 중에서 "access_token"의 값만 쏙 빼냅니다.
                return jsonNode.get("access_token").asText();
            } catch (Exception e) {
                log.error("구글 엑세스 토큰 파싱 에러", e);
                throw new RuntimeException("구글 엑세스 토큰 파싱 실패");
            }
        }
        return "구글 토큰 요청 실패";

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
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(userInfoJson);

        return UserDto.builder()
                .oauthProvider("GOOGLE")
                .oauthEmail(jsonNode.get("email").asText())
                .name(jsonNode.get("name").asText())
                .cellNum(null)
                .build();
    }
}