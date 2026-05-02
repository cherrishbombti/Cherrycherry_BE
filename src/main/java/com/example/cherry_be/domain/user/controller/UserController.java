package com.example.cherry_be.domain.user.controller;

import com.example.cherry_be.domain.user.dto.UserDto;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 소셜 로그인 후 정보를 저장하거나 로그인 처리하는 엔드포인트
    @PostMapping("/auth")
    public ResponseEntity<User> login(@RequestBody UserDto userDto) {
        User user = userService.loginOrSignup(userDto);
        return ResponseEntity.ok(user);
    }
}