package com.example.cherry_be.global.auth;

import com.example.cherry_be.global.exception.CustomException;
import com.example.cherry_be.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.security.Principal;

/**
 * 컨트롤러가 GuardianEmail·OrgId 파라미터를 선언하면 인증 정보에서 채워 넣는다.
 *
 * 이전에는 모든 엔드포인트가 Authentication 을 받아 getName() 을 꺼내 넘겼다.
 * 꺼낸 값이 String 이라 보호자용 메서드에 기관 ID 를 넘겨도 컴파일러가 잡지 못했고,
 * 같은 한 줄이 30곳 가까이 반복됐다.
 *
 * 역할(ROLE_USER / ROLE_ADMIN) 검증은 여기서 하지 않는다. SecurityConfig 가
 * 경로별로 이미 강제하고 있어 두 곳에서 같은 규칙을 관리하면 어긋나기 때문이다.
 */
@Slf4j
@Component
public class AuthPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> type = parameter.getParameterType();
        return type == GuardianEmail.class || type == OrgId.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Principal principal = webRequest.getUserPrincipal();

        if (!(principal instanceof Authentication authentication) || !authentication.isAuthenticated()) {
            // 인증이 필요한 경로는 SecurityConfig 가 막으므로 여기까지 오면 설정이 어긋난 것이다.
            // 사용자 잘못이 아니라 서버 설정 문제이므로 5xx 로 낸다.
            log.error("인증 주체 없이 컨트롤러에 도달함 - 보호 대상 경로가 SecurityConfig 에서 누락됐을 수 있다. 파라미터: {}",
                    parameter.getParameterType().getSimpleName());
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return parameter.getParameterType() == GuardianEmail.class
                ? new GuardianEmail(authentication.getName())
                : new OrgId(authentication.getName());
    }
}
