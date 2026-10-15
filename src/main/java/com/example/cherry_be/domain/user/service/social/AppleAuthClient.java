package com.example.cherry_be.domain.user.service.social;

import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sign in with Apple (네이티브 앱 로그인) 처리.
 *
 * 1) 앱이 보낸 identityToken(JWT)을 Apple 공개키(JWKS)로 검증 → 사용자 식별(sub, email)
 * 2) authorizationCode 를 Apple 에 보내 refresh token 교환 (탈퇴 시 revoke 용)
 * 3) 회원탈퇴 시 Apple 연결 해제(revoke) — App Store 심사 가이드라인 5.1.1(v)
 *
 * 2), 3)은 Apple 키(.p8)가 설정돼 있을 때만 동작한다. 설정이 없으면 건너뛰고 로그인은 정상 진행.
 */
@Slf4j
@Component
public class AppleAuthClient {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String KEYS_URL = "https://appleid.apple.com/auth/keys";
    private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";
    private static final String REVOKE_URL = "https://appleid.apple.com/auth/revoke";
    private static final Duration KEYS_TTL = Duration.ofHours(6);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 앱 번들 ID. identityToken 의 aud 와 같아야 한다. */
    @Value("${apple.client-id:kr.ac.knu.cherrishbomb.cherrishbombApp}")
    private String clientId;

    /** Apple Developer 팀 ID (10자리). revoke 용, 비어 있으면 revoke 기능 꺼짐 */
    @Value("${apple.team-id:}")
    private String teamId;

    /** Sign in with Apple 용 키 ID (10자리). */
    @Value("${apple.key-id:}")
    private String keyId;

    /** .p8 키 파일 경로 (EC2 의 secrets/ 등). */
    @Value("${apple.private-key-path:}")
    private String privateKeyPath;

    private volatile Map<String, PublicKey> keyCache = Map.of();
    private volatile Instant keyCacheAt = Instant.EPOCH;

    // ------------------------------------------------------------------
    // 1) identityToken 검증
    // ------------------------------------------------------------------

    /** 검증된 identityToken 의 핵심 값 */
    public record AppleUser(String sub, String email) {}

    public AppleUser verifyIdentityToken(String identityToken) {
        try {
            Claims claims = Jwts.parser()
                    .keyLocator(new LocatorAdapter<Key>() {
                        @Override
                        protected Key locate(JwsHeader header) {
                            return findKey(header.getKeyId());
                        }
                    })
                    .requireIssuer(APPLE_ISSUER)
                    .requireAudience(clientId)
                    .build()
                    .parseSignedClaims(identityToken)
                    .getPayload();

            String email = claims.get("email", String.class);
            return new AppleUser(claims.getSubject(), email);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.warn(">> Apple identityToken 검증 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.APPLE_TOKEN_INVALID);
        }
    }

    private PublicKey findKey(String kid) {
        PublicKey key = keyCache.get(kid);
        boolean expired = Instant.now().isAfter(keyCacheAt.plus(KEYS_TTL));
        if (key == null || expired) {
            // Apple 이 키를 교체했을 수 있으니 새로 받는다
            refreshKeys();
            key = keyCache.get(kid);
        }
        if (key == null) {
            throw new CustomException(ErrorCode.APPLE_TOKEN_INVALID);
        }
        return key;
    }

    private synchronized void refreshKeys() {
        try {
            String body = restTemplate.getForObject(KEYS_URL, String.class);
            JsonNode keys = objectMapper.readTree(body).get("keys");
            Map<String, PublicKey> map = new ConcurrentHashMap<>();
            KeyFactory kf = KeyFactory.getInstance("RSA");
            for (JsonNode k : keys) {
                BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(k.get("n").asText()));
                BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(k.get("e").asText()));
                map.put(k.get("kid").asText(), kf.generatePublic(new RSAPublicKeySpec(n, e)));
            }
            keyCache = map;
            keyCacheAt = Instant.now();
        } catch (Exception e) {
            log.error(">> Apple 공개키(JWKS) 조회 실패", e);
            throw new CustomException(ErrorCode.SOCIAL_LOGIN_FAILED);
        }
    }

    // ------------------------------------------------------------------
    // 2) authorizationCode → refresh token
    // ------------------------------------------------------------------

    /** Apple 키가 설정돼 있는지 (revoke 가능 여부) */
    public boolean isRevokeConfigured() {
        return !teamId.isBlank() && !keyId.isBlank() && !privateKeyPath.isBlank();
    }

    /** 실패하거나 설정이 없으면 null (로그인 자체는 막지 않는다) */
    public String exchangeRefreshToken(String authorizationCode) {
        if (!isRevokeConfigured() || authorizationCode == null || authorizationCode.isBlank()) {
            return null;
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", clientId);
            form.add("client_secret", createClientSecret());
            form.add("code", authorizationCode);
            form.add("grant_type", "authorization_code");

            String body = restTemplate.postForObject(TOKEN_URL, formEntity(form), String.class);
            JsonNode node = objectMapper.readTree(body);
            return node.hasNonNull("refresh_token") ? node.get("refresh_token").asText() : null;
        } catch (Exception e) {
            log.warn(">> Apple refresh token 교환 실패(로그인은 계속): {}", e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 3) 회원탈퇴 시 연결 해제
    // ------------------------------------------------------------------

    /** 실패해도 탈퇴는 계속 진행되도록 예외를 던지지 않는다. */
    public void revoke(String appleRefreshToken) {
        if (!isRevokeConfigured() || appleRefreshToken == null || appleRefreshToken.isBlank()) {
            log.info(">> Apple revoke 건너뜀 (키 미설정 또는 토큰 없음)");
            return;
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", clientId);
            form.add("client_secret", createClientSecret());
            form.add("token", appleRefreshToken);
            form.add("token_type_hint", "refresh_token");
            restTemplate.postForEntity(REVOKE_URL, formEntity(form), String.class);
            log.info(">> Apple 연결 해제(revoke) 완료");
        } catch (Exception e) {
            log.warn(">> Apple revoke 실패(탈퇴는 계속): {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------

    private HttpEntity<MultiValueMap<String, String>> formEntity(MultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return new HttpEntity<>(form, headers);
    }

    /** Apple 이 요구하는 client_secret: .p8 키로 서명한 ES256 JWT (최대 6개월, 여기선 5분) */
    private String createClientSecret() throws Exception {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(teamId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(5))))
                .audience().add(APPLE_ISSUER).and()
                .subject(clientId)
                .signWith(loadPrivateKey(), Jwts.SIG.ES256)
                .compact();
    }

    private PrivateKey loadPrivateKey() throws Exception {
        String pem = Files.readString(Path.of(privateKeyPath), StandardCharsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
