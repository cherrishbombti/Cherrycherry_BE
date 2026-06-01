package com.example.cherry_be.domain.ward.entity;

import com.example.cherry_be.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "emergency_contact")
public class EmergencyContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private String name;

    // 전화번호 (DB 컬럼명 유지)
    @Column(name = "phone_number", nullable = false)
    private String phone;

    // 관계 (DB 컬럼명 유지)
    @Column(name = "relation", nullable = false)
    private String relationship;

    @Builder
    public EmergencyContact(Member member, String name, String phone, String relationship) {
        this.member = member;
        this.name = name;
        this.phone = phone;
        this.relationship = relationship;
    }
}
