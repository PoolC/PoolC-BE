package org.poolc.api.member.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import org.poolc.api.member.domain.MemberRole;

@Getter
public class UpdateAdditionalRoleRequest {
    private final MemberRole role;
    private final boolean enabled;

    @JsonCreator
    public UpdateAdditionalRoleRequest(MemberRole role, Boolean enabled) {
        this.role = role;
        this.enabled = Boolean.TRUE.equals(enabled);
    }
}
