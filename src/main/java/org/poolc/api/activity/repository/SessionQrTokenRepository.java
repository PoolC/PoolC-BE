package org.poolc.api.activity.repository;

import org.poolc.api.activity.domain.SessionQrToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SessionQrTokenRepository extends JpaRepository<SessionQrToken, String> {
    Optional<SessionQrToken> findBySessionId(Long sessionId);

    @Query(value = "SELECT token.session_id AS sessionId, session.qr_enabled AS qrEnabled "
            + "FROM session_qr_token token JOIN session ON session.id = token.session_id "
            + "WHERE token.token = :token", nativeQuery = true)
    Optional<CheckInToken> findCheckInTokenByToken(@Param("token") String token);

    interface CheckInToken {
        Long getSessionId();
        boolean getQrEnabled();
    }
}
