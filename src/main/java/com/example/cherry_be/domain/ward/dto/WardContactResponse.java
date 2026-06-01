package com.example.cherry_be.domain.ward.dto;

import com.example.cherry_be.domain.ward.entity.EmergencyContact;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WardContactResponse {

    private Long contactId;      // 프론트 타입과 일치
    private String name;
    private String phone;        // phoneNumber → phone
    private String relationship; // relation → relationship
    private int priority;        // 순서 (임시: 등록 순서)

    public static WardContactResponse from(EmergencyContact contact) {
        return WardContactResponse.builder()
                .contactId(contact.getId())
                .name(contact.getName())
                .phone(contact.getPhone())
                .relationship(contact.getRelationship())
                .priority(0)
                .build();
    }
}
