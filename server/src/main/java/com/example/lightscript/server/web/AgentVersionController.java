package com.example.lightscript.server.web;

import com.example.lightscript.server.entity.AgentVersion;
import com.example.lightscript.server.service.AgentVersionService;
import com.example.lightscript.server.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class AgentVersionController {
    
    private final AgentVersionService versionService;
    
    /**
     * Agent 检查版本更新 (无需认证)
     */
    @GetMapping("/agent/version/check")
    public ResponseEntity<Map<String, Object>> checkVersion(
            @RequestParam String currentVersion,
            @RequestParam(defaultValue = "ALL") String platform) {
        
        AgentVersionService.VersionCheckResult result = versionService.checkForUpdate(currentVersion, platform);
        
        Map<String, Object> response = new HashMap<>();
        response.put("updateAvailable", result.isUpdateAvailable());
        response.put("message", result.getMessage());
        
        if (result.isUpdateAvailable() && result.getLatestVersion() != null) {
            AgentVersion latest = result.getLatestVersion();
            Map<String, Object> versionInfo = new HashMap<>();
            versionInfo.put("version", latest.getVersion());
            versionInfo.put("buildNumber", latest.getBuildNumber());
            versionInfo.put("releaseNotes", latest.getReleaseNotes());
            versionInfo.put("downloadUrl", latest.getDownloadUrl());
            versionInfo.put("fileSize", latest.getFileSize());
            versionInfo.put("fileHash", latest.getFileHash());
            versionInfo.put("forceUpgrade", latest.getForceUpgrade());
            versionInfo.put("minCompatibleVersion", latest.getMinCompatibleVersion());
            
            response.put("latestVersion", versionInfo);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取最新版本信息 (无需认证)
     */
    @GetMapping("/agent/version/latest")
    public ResponseEntity<AgentVersion> getLatestVersion() {
        Optional<AgentVersion> latest = versionService.getLatestVersion();
        return latest.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 获取当前版本信息 (无需认证)
     */
    @GetMapping("/agent/version/current")
    public ResponseEntity<AgentVersion> getCurrentVersion() {
        Optional<AgentVersion> current = versionService.getCurrentVersion();
        return current.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * 管理端：获取所有版本列表
     */
    @GetMapping("/web/agent-versions")
    @RequirePermission("system:settings")
    public ResponseEntity<List<AgentVersion>> getAllVersions() {
        List<AgentVersion> versions = versionService.getAllActiveVersions();
        return ResponseEntity.ok(versions);
    }
    
    /**
     * 管理端：根据平台获取版本列表
     */
    @GetMapping("/web/agent-versions/platform/{platform}")
    @RequirePermission("system:settings")
    public ResponseEntity<List<AgentVersion>> getVersionsByPlatform(@PathVariable String platform) {
        List<AgentVersion> versions = versionService.getVersionsByPlatform(platform.toUpperCase());
        return ResponseEntity.ok(versions);
    }
    
    /**
     * 管理端：从文件创建版本
     */
    @PostMapping("/web/agent-versions/from-file")
    @RequirePermission("system:settings")
    public ResponseEntity<?> createVersionFromFile(
            @RequestParam String fileId,
            @RequestParam String releaseNotes,
            Authentication authentication) {
        
        try {
            String createdBy = authentication.getName();
            AgentVersion created = versionService.createVersionFromFile(fileId, releaseNotes, createdBy);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to create version from file {}: {}", fileId, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "VALIDATION_ERROR");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Unexpected error creating version from file {}: {}", fileId, e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "INTERNAL_ERROR");
            error.put("message", "服务器内部错误，请稍后重试");
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * 管理端：更新强制升级状态
     */
    @PutMapping("/web/agent-versions/{id}/force-upgrade")
    @RequirePermission("system:settings")
    public ResponseEntity<Map<String, String>> updateForceUpgrade(
            @PathVariable Long id,
            @RequestParam boolean forceUpgrade) {
        
        versionService.updateForceUpgrade(id, forceUpgrade);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "强制升级状态已更新");
        return ResponseEntity.ok(response);
    }
    
    /**
     * 管理端：删除版本
     */
    @DeleteMapping("/web/agent-versions/{id}")
    @RequirePermission("system:settings")
    public ResponseEntity<Map<String, String>> deleteVersion(@PathVariable Long id) {
        versionService.deleteVersion(id);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "版本已删除");
        return ResponseEntity.ok(response);
    }
    
    /**
     * 管理端：获取版本统计信息
     */
    @GetMapping("/web/agent-versions/stats")
    @RequirePermission("system:settings")
    public ResponseEntity<Map<String, Object>> getVersionStats() {
        List<AgentVersion> allVersions = versionService.getAllActiveVersions();
        Optional<AgentVersion> latest = versionService.getLatestVersion();
        Optional<AgentVersion> current = versionService.getCurrentVersion();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalVersions", allVersions.size());
        stats.put("latestVersion", latest.map(AgentVersion::getVersion).orElse("N/A"));
        stats.put("currentVersion", current.map(AgentVersion::getVersion).orElse("N/A"));
        stats.put("hasForceUpgrade", allVersions.stream().anyMatch(v -> Boolean.TRUE.equals(v.getForceUpgrade())));
        
        return ResponseEntity.ok(stats);
    }
}