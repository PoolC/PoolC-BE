package org.poolc.api.project.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class GameBuildUploadResponse {
    private final String gameURL;
    private final List<UploadTarget> uploads;

    public GameBuildUploadResponse(String gameURL, List<UploadTarget> uploads) {
        this.gameURL = gameURL;
        this.uploads = uploads;
    }

    @Getter
    public static class UploadTarget {
        private final String path;
        private final String uploadURL;
        private final String contentType;
        private final String contentEncoding;

        public UploadTarget(String path, String uploadURL, String contentType, String contentEncoding) {
            this.path = path;
            this.uploadURL = uploadURL;
            this.contentType = contentType;
            this.contentEncoding = contentEncoding;
        }
    }
}
