package com.example.cherry_be.domain.organization.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignUpRequest {
    private String orgId;
    private String password;
    private String name;
}