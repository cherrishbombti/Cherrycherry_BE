package com.example.cherry_be.domain.member.entity;

import com.example.cherry_be.domain.log.entity.Log;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.user.entity.User;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "member_info")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기관(사회복지사) FK - 없을 수도 있어서 Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = true)
    private Organization organization;

    // 보호자(가족) FK - 없을 수도 있어서 Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    // 피보호자 이름
    @Column(nullable = false)
    private String name;

    // 나이
    private Long age;

    // 집 주소
    private String address;

    // 집이나 핸드폰 번호
    private String contact;

    // 보호자와의 관계 (어머니, 아버지 등)
    private String relationship;

    // 라즈베리파이 고유 ID (pi_node_01 같은 값)
    @Column(name = "device_mac", unique = true)
    private String deviceMac;

    // 현재 상태 (SAFE, WARNING, EMERGENCY)
    @Enumerated(EnumType.STRING)
    private MemberStatus status;

    // ── 최신 센서 상태 ──────────────────────────
    private Boolean vibrator;     // 진동 센서 정상 여부
    private Boolean radar;        // 레이더 센서 정상 여부
    private Boolean thermal;      // 열화상 센서 정상 여부

    @OneToMany(mappedBy = "member", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Log> fallLogs = new ArrayList<>();

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated; // 마지막 Pi 데이터 수신 시각

    @Builder
    public Member(Organization organization, User user, String name, Long age,
                  String address, String contact, String relationship, String deviceMac) {
        this.organization = organization;
        this.user = user;
        this.name = name;
        this.age = age;
        this.address = address;
        this.contact = contact;
        this.relationship = relationship;
        this.deviceMac = deviceMac;
        this.status = MemberStatus.SAFE;
        this.vibrator = true;
        this.radar = true;
        this.thermal = true;
        this.lastUpdated = LocalDateTime.now(); // 등록 시점으로 초기화
    }

    // 라즈베리파이 데이터 수신 시 상태 업데이트
    public void updateFromDevice(MemberStatus status,
                                 Boolean vibrator, Boolean radar, Boolean thermal) {
        this.status = status;
        this.vibrator = vibrator;
        this.radar = radar;
        this.thermal = thermal;
        this.lastUpdated = LocalDateTime.now();
    }
}
