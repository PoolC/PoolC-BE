package org.poolc.api.project.service;

import org.poolc.api.project.dto.GameBuildUploadRequest;
import org.poolc.api.project.dto.GameBuildUploadResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URLEncoder;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class GameBuildUploadService {
    private static final int MAX_FILES = 500;
    private static final long MAX_FILE_SIZE = 300L * 1024 * 1024;
    private static final long MAX_BUILD_SIZE = 300L * 1024 * 1024;

    private final ObjectProvider<S3Presigner> presignerProvider;
    private final String bucket;
    private final String publicBaseUrl;
    private final boolean enabled;

    public GameBuildUploadService(ObjectProvider<S3Presigner> presignerProvider,
                                  @Value("${game-build.s3.bucket:}") String bucket,
                                  @Value("${game-build.public-base-url:}") String publicBaseUrl,
                                  @Value("${game-build.enabled:false}") boolean enabled) {
        this.presignerProvider = presignerProvider;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
        this.enabled = enabled;
    }

    public boolean isConfigured() {
        return enabled && !bucket.isBlank() && !publicBaseUrl.isBlank();
    }

    public boolean isAllowedGameUrl(String gameURL) {
        if (gameURL == null || gameURL.isBlank()) return true;
        if (publicBaseUrl.isBlank()) return false;
        try {
            URI base = new URI(publicBaseUrl);
            URI candidate = new URI(gameURL);
            String path = candidate.getPath();
            String basePath = base.getPath() == null ? "" : base.getPath().replaceAll("/+$", "");
            String expectedGamesPath = basePath + "/games/";
            boolean secureUrl = "https".equalsIgnoreCase(candidate.getScheme());
            boolean localDevelopmentUrl = "http".equalsIgnoreCase(candidate.getScheme())
                    && ("localhost".equalsIgnoreCase(candidate.getHost())
                    || "127.0.0.1".equals(candidate.getHost())
                    || "::1".equals(candidate.getHost()));
            return (secureUrl || localDevelopmentUrl)
                    && base.getHost() != null
                    && base.getHost().equalsIgnoreCase(candidate.getHost())
                    && base.getPort() == candidate.getPort()
                    && candidate.getUserInfo() == null
                    && candidate.getQuery() == null
                    && candidate.getFragment() == null
                    && path != null
                    && candidate.normalize().getPath().equals(path)
                    && path.startsWith(expectedGamesPath)
                    && path.toLowerCase(Locale.ROOT).endsWith(".html");
        } catch (URISyntaxException error) {
            return false;
        }
    }

    public GameBuildUploadResponse createUploadTargets(GameBuildUploadRequest request) {
        if (!isConfigured()) {
            throw new IllegalStateException("게임 빌드 저장소가 설정되지 않았습니다.");
        }
        List<GameBuildUploadRequest.FileEntry> files = request.getFiles();
        if (files == null || files.isEmpty() || files.size() > MAX_FILES) {
            throw new IllegalArgumentException("게임 파일은 1~500개까지 업로드할 수 있습니다.");
        }

        Set<String> paths = new HashSet<>();
        long totalSize = 0;
        for (GameBuildUploadRequest.FileEntry file : files) {
            if (file == null) {
                throw new IllegalArgumentException("게임 파일 정보가 올바르지 않습니다.");
            }
            String path = normalizePath(file.getPath());
            if (!paths.add(path)) {
                throw new IllegalArgumentException("중복된 게임 파일 경로가 있습니다.");
            }
            if (file.getSize() < 0 || file.getSize() > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("파일 하나는 300MB 이하이어야 합니다.");
            }
            if (file.getSize() > MAX_BUILD_SIZE - totalSize) {
                throw new IllegalArgumentException("게임 빌드 전체 크기는 300MB 이하이어야 합니다.");
            }
            totalSize += file.getSize();
        }

        String entrypoint = normalizePath(request.getEntrypoint());
        if (!paths.contains(entrypoint) || !entrypoint.toLowerCase(Locale.ROOT).endsWith(".html")) {
            throw new IllegalArgumentException("실행할 HTML 파일이 게임 파일 목록에 없습니다.");
        }

        String releaseId = UUID.randomUUID().toString();
        S3Presigner presigner = presignerProvider.getIfAvailable();
        if (presigner == null) {
            throw new IllegalStateException("게임 빌드 업로드 서명기가 설정되지 않았습니다.");
        }
        List<GameBuildUploadResponse.UploadTarget> targets = new ArrayList<>();
        for (GameBuildUploadRequest.FileEntry file : files) {
            String path = normalizePath(file.getPath());
            String key = "games/" + releaseId + "/" + path;
            String contentType = contentType(path);
            String contentEncoding = path.toLowerCase(Locale.ROOT).endsWith(".br") ? "br" : null;
            PutObjectRequest.Builder putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType);
            if (contentEncoding != null) {
                putRequest.contentEncoding(contentEncoding);
            }
            String uploadURL = presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(30))
                    .putObjectRequest(putRequest.build())
                    .build()).url().toString();
            targets.add(new GameBuildUploadResponse.UploadTarget(path, uploadURL, contentType, contentEncoding));
        }

        String baseUrl = publicBaseUrl.replaceAll("/+$", "");
        String encodedEntrypoint = encodePath(entrypoint);
        return new GameBuildUploadResponse(baseUrl + "/games/" + releaseId + "/" + encodedEntrypoint, targets);
    }

    private String normalizePath(String value) {
        if (value == null || value.isBlank() || value.length() > 512 || value.startsWith("/") || value.contains("\\")) {
            throw new IllegalArgumentException("게임 파일 경로가 올바르지 않습니다.");
        }
        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..") || segment.contains(":") || segment.contains("?")) {
                throw new IllegalArgumentException("게임 파일 경로가 올바르지 않습니다.");
            }
        }
        return value;
    }

    private String contentType(String path) {
        String lowerPath = path.toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith(".wasm.br") || lowerPath.endsWith(".wasm")) return "application/wasm";
        if (lowerPath.endsWith(".js.br") || lowerPath.endsWith(".js")) return "application/javascript";
        if (lowerPath.endsWith(".css.br") || lowerPath.endsWith(".css")) return "text/css; charset=utf-8";
        if (lowerPath.endsWith(".html")) return "text/html; charset=utf-8";
        if (lowerPath.endsWith(".json")) return "application/json";
        if (lowerPath.endsWith(".png")) return "image/png";
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) return "image/jpeg";
        if (lowerPath.endsWith(".webp")) return "image/webp";
        if (lowerPath.endsWith(".svg")) return "image/svg+xml";
        if (lowerPath.endsWith(".mp3")) return "audio/mpeg";
        if (lowerPath.endsWith(".ogg")) return "audio/ogg";
        if (lowerPath.endsWith(".mp4")) return "video/mp4";
        if (lowerPath.endsWith(".br")) return "application/octet-stream";
        return "application/octet-stream";
    }

    private String encodePath(String path) {
        String[] segments = path.split("/");
        List<String> encodedSegments = new ArrayList<>();
        for (String segment : segments) {
            encodedSegments.add(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return String.join("/", encodedSegments);
    }
}
