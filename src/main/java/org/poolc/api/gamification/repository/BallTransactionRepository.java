package org.poolc.api.gamification.repository;

import org.poolc.api.gamification.domain.BallTransaction;
import org.poolc.api.gamification.domain.BallTransactionType;
import org.poolc.api.gamification.domain.BallType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface BallTransactionRepository extends JpaRepository<BallTransaction, Long> {
    @Query("select coalesce(sum(t.amount), 0) from BallTransaction t where t.member.UUID = :memberUuid")
    long getBalanceByMemberUuid(@Param("memberUuid") String memberUuid);

    @Query("select coalesce(sum(t.amount), 0) from BallTransaction t where t.member.UUID = :memberUuid and t.ballType = :ballType")
    long getBalanceByMemberUuidAndBallType(@Param("memberUuid") String memberUuid, @Param("ballType") BallType ballType);

    @Query("select coalesce(sum(t.amount), 0) from BallTransaction t "
            + "where t.member.UUID = :memberUuid and t.type = :type and t.sourceType = :sourceType and t.sourceId = :sourceId")
    long getAmountByMemberUuidAndTypeAndSource(
            @Param("memberUuid") String memberUuid,
            @Param("type") BallTransactionType type,
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId);

    @Query("select t.sourceId from BallTransaction t where t.type = :type and t.sourceType in :sourceTypes")
    List<String> findDrawSourceIds(@Param("type") BallTransactionType type, @Param("sourceTypes") List<String> sourceTypes);

    @Query("select t from BallTransaction t join fetch t.member left join fetch t.grantedBy "
            + "where t.type = :type and t.amount > 0 and t.sourceType in :sourceTypes "
            + "order by t.createdAt desc, t.id desc")
    List<BallTransaction> findAdminBallGrants(
            @Param("type") BallTransactionType type, @Param("sourceTypes") List<String> sourceTypes);
}
