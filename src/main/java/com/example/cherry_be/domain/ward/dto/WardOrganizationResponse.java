package com.example.cherry_be.domain.ward.dto;

import com.example.cherry_be.domain.organization.entity.Organization;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * 피보호자의 기관 연동 상태.
 * 연동 전에는 linked=false 이고 나머지 값은 모두 null 이다.
 */
@Getter
@Builder
public class WardOrganizationResponse {

    // boolean 이 아닌 Boolean 이라도 Jackson 은 isLinked → linked 로 직렬화하므로 이름을 명시한다
    @JsonProperty("linked")
    private final boolean linked;

    private final Long organizationId;
    private final String organizationName;
    private final Long orgCode;

    public static WardOrganizationResponse notLinked() {
        return WardOrganizationResponse.builder().linked(false).build();
    }

    public static WardOrganizationResponse from(Organization organization) {
        return WardOrganizationResponse.builder()
                .linked(true)
                .organizationId(organization.getId())
                .organizationName(organization.getName())
                .orgCode(organization.getOrgCode())
                .build();
    }
}
