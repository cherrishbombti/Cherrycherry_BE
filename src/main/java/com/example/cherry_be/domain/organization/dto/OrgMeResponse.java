package com.example.cherry_be.domain.organization.dto;

import com.example.cherry_be.domain.organization.entity.Organization;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrgMeResponse {

    private String name;   // 헤더에 표시할 기관명(담당자명)
    private String orgId;  // 로그인 아이디

    public static OrgMeResponse from(Organization org) {
        return OrgMeResponse.builder()
                .name(org.getName())
                .orgId(org.getOrgId())
                .build();
    }
}
