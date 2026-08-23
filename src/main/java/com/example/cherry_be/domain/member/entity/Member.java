package com.example.cherry_be.domain.member.entity;

import com.example.cherry_be.domain.log.entity.Log;
import com.example.cherry_be.domain.organization.entity.Organization;
import com.example.cherry_be.domain.user.entity.User;
import com.example.cherry_be.domain.ward.entity.EmergencyContact;
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

    // 온라인 판정 기준 (분) - 협의 후 조정 가능
    private static final long ONLINE_THRESHOLD_MINUTES = 5;

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

    // 현재 상태 (SAFE, WARNING, DANGER)
    @Enumerated(EnumType.STRING)
    private MemberStatus status;

    // ── 최신 센서 상태 ──────────────────────────
    private Boolean vibrator;     // 진동 센서 정상 여부
    private Boolean radar;        // 레이더 센서 정상 여부
    private Boolean thermal;      // 열화상 센서 정상 여부

    // ── 피보호자에 종속된 데이터 ──────────────────────
    // 피보호자가 사라지면 함께 사라져야 하는 것들. 남아있으면 FK 제약에 걸려
    // 피보호자 삭제 자체가 실패하므로 cascade 로 함께 정리한다.
    //
    // 여기에 두지 않고 MemberService.deleteMember 에서 직접 지우는 것들:
    //  - notification : 한 피보호자에 수천 건이 쌓일 수 있어 컬렉션으로 들고 있으면
    //                   전체가 메모리에 올라올 위험이 있다.
    //  - member_health: @OneToOne 은 기본이 EAGER 라 Member 를 읽을 때마다 함께 읽힌다.
    //                   건강정보는 암호화 컬럼이어서 읽을 때마다 복호화가 일어나는데,
    //                   대시보드 목록처럼 건강정보를 쓰지 않는 조회에서도 전원분이 복호화된다.
    //                   게다가 한 건이라도 복호화에 실패하면 목록 조회 전체가 실패한다.

    @OneToMany(mappedBy = "member", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Log> fallLogs = new ArrayList<>();

    @OneToMany(mappedBy = "member", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<EmergencyContact> emergencyContacts = new ArrayList<>();

    // 마지막으로 디바이스 신호를 받은 "서버 수신 시각" (온라인 판정용)
    // 등록 시점에는 null - 한 번도 신호를 받지 않은 상태와 오프라인을 구분하기 위함
    @Column(name = "device_last_seen")
    private LocalDateTime deviceLastSeen;

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
        // 센서값은 기기 신호를 받기 전까지 null 유지
        // (통신한 적이 없는데 true로 두면 '센서 정상'이라는 근거 없는 사실을 표시하게 됨)
        this.vibrator = null;
        this.radar = null;
        this.thermal = null;
        // deviceLastSeen은 null 유지 (아직 기기 신호를 받은 적 없음)
    }

    // 라즈베리파이 데이터 수신 시 상태 업데이트
    public void updateFromDevice(MemberStatus status,
                                 Boolean vibrator, Boolean radar, Boolean thermal) {
        this.status = status;
        this.vibrator = vibrator;
        this.radar = radar;
        this.thermal = thermal;
        // 기기가 보낸 timestamp가 아닌 "서버 수신 시각" 기준으로 고정
        // (기기 시계가 틀어져도 연결 생존 판정은 정확해야 하므로)
        this.deviceLastSeen = LocalDateTime.now();
    }

    /**
     * 보호자가 입력한 기관번호로 기관을 연결한다.
     *
     * 등록(소유)과 조회 권한은 별개다. 여기서 organization 이 채워져도
     * user 는 그대로 남으므로, 이 피보호자의 소유자는 여전히 보호자다.
     * 기관은 대시보드에서 조회만 할 수 있다 (isManageable 참고).
     */
    public void linkOrganization(Organization organization) {
        this.organization = organization;
    }

    /** 기관 연동 해제. 보호자만 호출한다. */
    public void unlinkOrganization() {
        this.organization = null;
    }

    /**
     * 기관이 이 피보호자를 관리(삭제·건강정보 수정)할 수 있는지 여부 (저장하지 않고 계산).
     *
     * 보호자가 등록한 피보호자는 보호자가 소유자이므로 기관은 조회만 가능하다.
     * 기관이 직접 등록한 피보호자(무연고자)만 기관이 관리한다.
     */
    public boolean isManageable() {
        return this.user == null;
    }

    /**
     * 디바이스 온라인 여부 (저장하지 않고 계산)
     * - deviceLastSeen이 null이면 false (한 번도 수신 없음 = 연결 대기 중)
     * - 최근 ONLINE_THRESHOLD_MINUTES 이내 수신이면 true
     */
    public boolean isDeviceOnline() {
        return this.deviceLastSeen != null
                && this.deviceLastSeen.isAfter(
                        LocalDateTime.now().minusMinutes(ONLINE_THRESHOLD_MINUTES));
    }
}
