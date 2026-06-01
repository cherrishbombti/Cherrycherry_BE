package com.example.cherry_be.domain.ward.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WardContactRequest {
    private String name;         // 연락처 이름
    private String phone;        // 전화번호
    private String relationship; // 관계
}
