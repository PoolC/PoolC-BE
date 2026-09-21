package org.poolc.api.activity.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import org.poolc.api.activity.domain.Session;

import java.time.LocalDate;
import java.util.List;

@Getter
public class SessionResponse {

    private final Long id;
    private final Long sessionNumber;
    private final LocalDate date;
    private final String description;
    private final Long hour;
    private final List<String> fileList;
    private final boolean qrEnabled;

    @JsonCreator
    public SessionResponse(Long id, Long sessionNumber, LocalDate date, String description, Long hour, List<String> fileList, boolean qrEnabled) {
        this.id = id;
        this.sessionNumber = sessionNumber;
        this.date = date;
        this.description = description;
        this.hour = hour;
        this.fileList = fileList;
        this.qrEnabled = qrEnabled;
    }

    public static SessionResponse of(Session session) {
        return new SessionResponse(session.getId(), session.getSessionNumber(), session.getDate(), session.getDescription(), session.getHour(), session.getFileList(), session.isQrEnabled());
    }
}
