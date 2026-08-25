package com.example.cherry_be.domain.ward.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UnreadCountResponse {
    private long unreadCount;   // 미읽음 알림 개수 (배지용)
}