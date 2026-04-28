package com.example.cherry_be.domain.user.service;

import com.example.cherry_be.domain.user.dto.UserDto;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.repository.UserRepository;
import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User saveOrUpdate(UserDto userDto) {
        return userRepository.findByOauthEmail(userDto.getOauthEmail())
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .oauthEmail(userDto.getOauthEmail())
                            .name(userDto.getName())
                            .oauthProvider(SocialLoginType.valueOf(userDto.getOauthProvider().toUpperCase()))
                            .cellNum(userDto.getCellNum())
                            .build();
                    return userRepository.save(newUser);
                });
    }
}