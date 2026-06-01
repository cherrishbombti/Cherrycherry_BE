package com.example.cherry_be.domain.user.controller;

import com.example.cherry_be.domain.user.dto.UserDto;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.service.UserService;
import com.example.cherry_be.domain.user.service.UserService.LoginResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/auth")
    public ResponseEntity<User> login(@RequestBody UserDto userDto) {
        LoginResult result = userService.loginOrSignup(userDto);
        return ResponseEntity.ok(result.getUser());
    }
}
