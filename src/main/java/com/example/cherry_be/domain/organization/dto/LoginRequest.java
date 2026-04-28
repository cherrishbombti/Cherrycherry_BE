package com.example.cherry_be.domain.organization.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LoginRequest {
    private String orgId;
    private String password;
}