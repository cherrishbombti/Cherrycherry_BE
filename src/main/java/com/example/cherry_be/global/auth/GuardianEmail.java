package com.example.cherry_be.global.auth;

/**
 * 인증된 보호자(ROLE_USER)의 식별자. JWT subject 에 담긴 소셜 이메일이다.
 *
 * String 으로 주고받으면 기관 ID(OrgId)와 섞여 들어가도 컴파일러가 잡지 못한다.
 * 두 값 모두 JWT subject 에서 나와 생김새가 비슷하고, 서비스 메서드는
 * 하나같이 "인증 주체 + 대상 id" 형태라 인자 순서도 닮아 있다.
 * 잘못 넘어가도 예외 없이 "찾을 수 없음" 으로만 끝나 원인을 찾기 어렵다.
 *
 * 컨트롤러 파라미터로 선언하면 AuthPrincipalArgumentResolver 가 채워준다.
 */
public record GuardianEmail(String value) {
}
