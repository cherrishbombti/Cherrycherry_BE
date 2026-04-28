package com.example.cherry_be.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class UserDto {
    private String oauthProvider;
    private String oauthEmail;
    private String name;
    private String cellNum;
}