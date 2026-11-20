package org.poolc.api.project.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.PositiveOrZero;
import java.util.List;

@Getter
public class GameBuildUploadRequest {
    @NotEmpty
    @Valid
    private final List<FileEntry> files;

    @NotBlank
    private final String entrypoint;

    @JsonCreator
    public GameBuildUploadRequest(@JsonProperty("files") List<FileEntry> files,
                                  @JsonProperty("entrypoint") String entrypoint) {
        this.files = files;
        this.entrypoint = entrypoint;
    }

    @Getter
    public static class FileEntry {
        @NotBlank
        private final String path;
        @PositiveOrZero
        private final long size;

        @JsonCreator
        public FileEntry(@JsonProperty("path") String path, @JsonProperty("size") long size) {
            this.path = path;
            this.size = size;
        }
    }
}
