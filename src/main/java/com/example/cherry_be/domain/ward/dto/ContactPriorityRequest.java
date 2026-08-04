package com.example.cherry_be.domain.ward.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
@NoArgsConstructor
public class ContactPriorityRequest {

    private List<ContactPriorityItem> contacts;

    @Getter
    @NoArgsConstructor
    public static class ContactPriorityItem {
        private Long contactId;
        private int priority;
    }
}
