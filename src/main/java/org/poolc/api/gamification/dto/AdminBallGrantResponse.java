package org.poolc.api.gamification.dto;

import lombok.Getter;
import org.poolc.api.gamification.domain.BallTransaction;
import org.poolc.api.gamification.domain.BallType;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
public class AdminBallGrantResponse {
    private final Long transactionId;
    private final String memberLoginID;
    private final String memberName;
    private final BallType ballType;
    private final int amount;
    private final String grantorName;
    private final OffsetDateTime grantedAt;

    public AdminBallGrantResponse(BallTransaction transaction) {
        this.transactionId = transaction.getId();
        this.memberLoginID = transaction.getMember().getLoginID();
        this.memberName = transaction.getMember().getName();
        this.ballType = transaction.getBallType();
        this.amount = transaction.getAmount();
        this.grantorName = transaction.getGrantedBy() == null ? null : transaction.getGrantedBy().getName();
        this.grantedAt = transaction.getCreatedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
    }
}
