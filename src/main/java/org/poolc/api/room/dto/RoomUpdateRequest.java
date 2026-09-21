package org.poolc.api.room.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
public class RoomUpdateRequest {

    private final LocalDate date;
    private final LocalTime start;
    private final LocalTime end;
    private final String purpose;
    private final boolean sharedUseAllowed;

    public RoomUpdateRequest(LocalDate date, LocalTime start, LocalTime end, String purpose) {
        this(date, start, end, purpose, false);
    }

    @JsonCreator
    public RoomUpdateRequest(@JsonProperty("date") LocalDate date,
                             @JsonProperty("start") LocalTime start,
                             @JsonProperty("end") LocalTime end,
                             @JsonProperty("purpose") String purpose,
                             @JsonProperty("sharedUseAllowed") boolean sharedUseAllowed) {
        this.date = date;
        this.start = start;
        this.end = end;
        this.purpose = purpose;
        this.sharedUseAllowed = sharedUseAllowed;
    }
}
