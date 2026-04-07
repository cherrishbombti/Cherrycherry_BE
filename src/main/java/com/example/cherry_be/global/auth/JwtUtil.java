package com.example.cherry_be.global.auth; // 패키지명은 본인 프로젝트에 맞게 확인해 주세요!

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component // 스프링에게 "이거 공용 도구니까 네가 잘 관리해 줘!" 라고 맡기는 어노테이션
public class JwtUtil {

    private final SecretKey secretKey;
    private final Long expirationTime;

    // 1. yml에 적어둔 비밀키와 만료시간을 가져와서 세팅합니다.
    public JwtUtil(@Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-time}") Long expirationTime) {
        // yml의 평범한 문자열 -> JWT 전용 '암호화 열쇠'
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationTime = expirationTime;
    }

    /**
     * 2. 로그인 성공 시 토큰을 생성하는 메서드
     */
    public String createToken(String loginId, String role) {
        return Jwts.builder()
                .claim("loginId", loginId) // 토큰에 아이디 담기
                .claim("role", role)       // 토큰에 권한 담기 (예: ROLE_ADMIN)
                .issuedAt(new Date(System.currentTimeMillis())) // 발급 시간
                .expiration(new Date(System.currentTimeMillis() + expirationTime)) // 만료 시간
                .signWith(secretKey)
                .compact();
    }

    /**
     * 3. 나중에 프론트가 토큰을 가져왔을 때, 토큰 안에서 아이디(loginId)를 꺼내는 메서드
     */
    public String getLoginId(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("loginId", String.class);
    }

    /**
     * 4. 프론트가 가져온 토큰이 가짜인지, 만료되었는지 검사하는 메서드
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true; // 정상 토큰이면 true
        } catch (Exception e) {
            return false; // 문제 있는 토큰이면 false
        }
    }
}
