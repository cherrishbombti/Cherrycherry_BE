package com.example.cherry_be.global.auth;

/**
 * 인증된 기관(ROLE_ADMIN)의 식별자. JWT subject 에 담긴 기관 로그인 ID 다.
 *
 * 회원가입·로그인이 요청 본문으로 받는 기관 ID 는 아직 인증된 값이 아니므로
 * 여기 해당하지 않는다. 그쪽은 String 그대로 둔다.
 *
 * 컨트롤러 파라미터로 선언하면 AuthPrincipalArgumentResolver 가 채워준다.
 * 자세한 배경은 {@link GuardianEmail} 참고.
 */
public record OrgId(String value) {
}
