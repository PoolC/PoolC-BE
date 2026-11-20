package org.poolc.api.gamification.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.poolc.api.gamification.domain.BallType;

@Getter
@Setter
@NoArgsConstructor
public class AdminBallGrantRequest {
    private String loginID;
    private BallType ballType;
    private Integer amount;
}
