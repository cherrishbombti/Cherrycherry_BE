package com.example.cherry_be.domain.push.dto;

import com.example.cherry_be.domain.push.entity.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DeviceTokenRequest {

    @NotBlank(message = "토큰은 필수입니다.")
    private String token;

    @NotNull(message = "플랫폼은 필수입니다. (WEB / ANDROID / IOS)")
    private DevicePlatform platform;
}
