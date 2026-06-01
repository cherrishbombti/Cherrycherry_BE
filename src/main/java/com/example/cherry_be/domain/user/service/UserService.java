package com.example.cherry_be.domain.user.service;

import com.example.cherry_be.domain.user.dto.UserDto;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.repository.UserRepository;
import com.example.cherry_be.domain.user.helper.constants.SocialLoginType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // 로그인/회원가입 결과를 담는 내부 클래스
    @Getter
    public static class LoginResult {
        private final User user;
        private final boolean isNewUser;

        public LoginResult(User user, boolean isNewUser) {
            this.user = user;
            this.isNewUser = isNewUser;
        }
    }

    @Transactional
    public LoginResult loginOrSignup(UserDto userDto) {
        return userRepository.findByOauthEmail(userDto.getOauthEmail())
                .map(existingUser -> new LoginResult(existingUser, false)) // 기존 유저
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .oauthEmail(userDto.getOauthEmail())
                            .name(userDto.getName())
                            .oauthProvider(SocialLoginType.valueOf(userDto.getOauthProvider().toUpperCase()))
                            .cellNum(userDto.getCellNum())
                            .build();
                    return new LoginResult(userRepository.save(newUser), true); // 신규 유저
                });
    }
}
