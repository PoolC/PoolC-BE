package org.poolc.api.project.dto;

import org.poolc.api.project.domain.ProjectCategory;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

@Getter
public class UpdateProjectRequest {

    private final String name;
    private final String description;
    private final String genre;
    @NotNull(message = "프로젝트 카테고리는 필수입니다.")
    private final ProjectCategory category;
    @NotNull(message = "프로젝트 시작일은 필수입니다.")
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String thumbnailURL;
    private final String body;
    private final String gameURL;
    private final String downloadURL;
    List<String> memberLoginIDs;

    public UpdateProjectRequest(String name, String description, String genre, ProjectCategory category, LocalDate startDate, LocalDate endDate, String thumbnailURL, String body, List<String> memberLoginIDs) {
        this(name, description, genre, category, startDate, endDate, thumbnailURL, body, null, null, memberLoginIDs);
    }

    public UpdateProjectRequest(String name, String description, String genre, ProjectCategory category, LocalDate startDate, LocalDate endDate, String thumbnailURL, String body, String gameURL, List<String> memberLoginIDs) {
        this(name, description, genre, category, startDate, endDate, thumbnailURL, body, gameURL, null, memberLoginIDs);
    }

    @JsonCreator
    public UpdateProjectRequest(String name, String description, String genre, ProjectCategory category, LocalDate startDate, LocalDate endDate, String thumbnailURL, String body, String gameURL, String downloadURL, List<String> memberLoginIDs) {
        this.name = name;
        this.description = description;
        this.genre = genre;
        this.category = category;
        this.startDate = startDate;
        this.endDate = endDate;
        this.thumbnailURL = thumbnailURL;
        this.body = body;
        this.gameURL = gameURL;
        this.downloadURL = downloadURL;
        this.memberLoginIDs = memberLoginIDs;
    }

    @AssertTrue(message = "프로젝트 종료일은 시작일보다 빠를 수 없습니다.")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
