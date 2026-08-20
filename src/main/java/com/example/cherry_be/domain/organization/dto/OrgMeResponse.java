package com.example.cherry_be.domain.organization.dto;

import com.example.cherry_be.domain.organization.entity.Organization;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrgMeResponse {

    private String name;   // 헤더에 표시할 기관명(담당자명)
    private String orgId;  // 로그인 아이디

    // 기관번호 — 보호자에게 안내해 피보호자를 연동시킬 때 쓴다.
    // 자동 부여 전에 만들어진 계정은 null 일 수 있다.
    private Long orgCode;

    public static OrgMeResponse from(Organization org) {
        return OrgMeResponse.builder()
                .name(org.getName())
                .orgId(org.getOrgId())
                .orgCode(org.getOrgCode())
                .build();
    }
}
