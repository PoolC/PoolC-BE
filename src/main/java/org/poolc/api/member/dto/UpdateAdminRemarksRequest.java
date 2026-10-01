package org.poolc.api.member.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public class UpdateAdminRemarksRequest {
    private final String remarks;

    @JsonCreator
    public UpdateAdminRemarksRequest(String remarks) {
        this.remarks = remarks;
    }
}
