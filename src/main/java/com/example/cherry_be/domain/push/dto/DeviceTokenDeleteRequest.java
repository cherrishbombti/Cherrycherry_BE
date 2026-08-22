package com.example.cherry_be.domain.push.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 토큰 삭제(로그아웃) 요청.
 *
 * 쿼리스트링이 아니라 본문으로 받는 이유:
 * URL 은 nginx 액세스 로그·애플리케이션 로그·프록시에 그대로 기록된다.
 * HTTPS 는 전송 구간만 보호할 뿐 서버 도착 후에는 평문이라,
 * 특정 사용자의 기기를 지목하는 FCM 토큰이 로그에 남게 된다.
 *
 * 등록용 DeviceTokenRequest 를 재사용하지 않는 것은 platform 이 삭제에 불필요하기 때문이다.
 */
@Getter
@NoArgsConstructor
public class DeviceTokenDeleteRequest {

    @NotBlank(message = "토큰은 필수입니다.")
    private String token;
}
