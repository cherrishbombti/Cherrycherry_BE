package com.example.cherry_be.domain.log.dto;

import com.example.cherry_be.domain.log.entity.Log;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 낙상 이력 페이지 응답.
 * Spring Page를 그대로 직렬화하면 포맷이 불안정하므로 필요한 필드만 담아 내려준다.
 */
@Getter
@Builder
public class LogPageResponse {

    private List<LogResponse> content;
    private int page;             // 현재 페이지 (0부터)
    private int size;             // 페이지 크기
    private long totalElements;   // 전체 건수
    private int totalPages;       // 전체 페이지 수
    private boolean last;         // 마지막 페이지 여부

    public static LogPageResponse from(Page<Log> logPage) {
        return LogPageResponse.builder()
                .content(logPage.getContent().stream().map(LogResponse::from).toList())
                .page(logPage.getNumber())
                .size(logPage.getSize())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .last(logPage.isLast())
                .build();
    }
}
