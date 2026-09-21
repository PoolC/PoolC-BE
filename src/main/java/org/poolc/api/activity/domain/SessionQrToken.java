package org.poolc.api.activity.domain;

import lombok.Getter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Getter
@Table(name = "session_qr_token")
public class SessionQrToken {
    @Id
    @Column(length = 64)
    private String token;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private Session session;

    protected SessionQrToken() {
    }

    public SessionQrToken(String token, Session session) {
        this.token = token;
        this.session = session;
    }
}
