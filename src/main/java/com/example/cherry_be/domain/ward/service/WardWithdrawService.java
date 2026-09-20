package com.example.cherry_be.domain.ward.service;

import com.example.cherry_be.domain.health.service.MemberHealthService;
import com.example.cherry_be.domain.member.repository.MemberRepository;
import com.example.cherry_be.domain.notification.service.NotificationService;
import com.example.cherry_be.domain.push.repository.DeviceTokenRepository;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.user.repository.UserRepository;
import com.example.cherry_be.global.auth.GuardianEmail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원탈퇴(보호자 계정 삭제). 보호자와 연결된 모든 데이터를 함께 지운다.
 */
@Service
@RequiredArgsConstructor
public class WardWithdrawService {

    private final WardFinder wardFinder;
    private final MemberRepository memberRepository;
    private final MemberHealthService memberHealthService;
    private final NotificationService notificationService;
    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;


    @Transactional
    public void withdraw(GuardianEmail oauthEmail) {
        User guardian = wardFinder.getGuardian(oauthEmail);

        // 1) 이 보호자의 푸시 토큰 삭제
        deviceTokenRepository.deleteAll(deviceTokenRepository.findByUser(guardian));

        // 2) 연결된 피보호자(있으면) + 파생 데이터 삭제
        memberRepository.findByUser(guardian).ifPresent(ward -> {
            notificationService.deleteByMember(ward);
            memberHealthService.deleteByMember(ward);
            memberRepository.delete(ward);
        });

        // 3) 보호자 계정 삭제
        userRepository.delete(guardian);
    }
}