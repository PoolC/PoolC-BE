package org.poolc.api.project.controller;

import lombok.RequiredArgsConstructor;
import org.poolc.api.project.dto.GameBuildUploadRequest;
import org.poolc.api.project.dto.GameBuildUploadResponse;
import org.poolc.api.project.service.GameBuildUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Collections;

@RestController
@RequiredArgsConstructor
@RequestMapping("/project/game-build")
public class GameBuildUploadController {
    private final GameBuildUploadService gameBuildUploadService;

    @PostMapping("/upload-targets")
    public ResponseEntity<?> createUploadTargets(@Valid @RequestBody GameBuildUploadRequest request) {
        if (!gameBuildUploadService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Collections.singletonMap("message", "게임 저장소가 아직 설정되지 않았습니다."));
        }
        try {
            GameBuildUploadResponse response = gameBuildUploadService.createUploadTargets(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", error.getMessage()));
        }
    }

    @DeleteMapping("/{releaseId}")
    public ResponseEntity<?> deleteGameBuild(@PathVariable String releaseId) {
        if (!gameBuildUploadService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Collections.singletonMap("message", "게임 저장소가 아직 설정되지 않았습니다."));
        }
        try {
            gameBuildUploadService.deleteGameBuildByReleaseId(releaseId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", error.getMessage()));
        }
    }
}
