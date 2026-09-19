package com.example.cherry_be.global.config;

import com.example.cherry_be.global.auth.AuthPrincipalArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthPrincipalArgumentResolver authPrincipalArgumentResolver;

    /** 컨트롤러가 GuardianEmail·OrgId 를 직접 파라미터로 받을 수 있게 한다. */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authPrincipalArgumentResolver);
    }
}
