package com.example.lightscript.server.service;

import com.example.lightscript.server.entity.AgentVersion;
import com.example.lightscript.server.repository.AgentVersionRepository;
import com.example.lightscript.server.model.FileModels.FileDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentVersionService {
    
    private final AgentVersionRepository versionRepository;
    private final FileService fileService;
    
    /**
     * 获取最新版本（基于版本号自动判断）
     */
    public Optional<AgentVersion> getLatestVersion() {
        List<AgentVersion> activeVersions = versionRepository.findByStatusOrderByCreatedAtDesc("ACTIVE");
        if (activeVersions.isEmpty()) {
            return Optional.empty();
        }
        
        // 找到版本号最高的版本
        return activeVersions.stream()
            .max((v1, v2) -> compareVersions(v1.getVersion(), v2.getVersion()));
    }
    
    /**
     * 获取当前版本（已废弃 - 使用 getLatestVersion() 代替）
     * @deprecated 使用基于版本号的自动判断
     */
    @Deprecated
    public Optional<AgentVersion> getCurrentVersion() {
        return getLatestVersion(); // 直接返回最新版本
    }
    
    /**
     * 根据版本号获取版本信息
     */
    public Optional<AgentVersion> getVersionByNumber(String version) {
        return versionRepository.findByVersionAndStatus(version, "ACTIVE");
    }
    
    /**
     * 检查版本更新（基于版本号自动判断）
     */
    public VersionCheckResult checkForUpdate(String currentVersion, String platform) {
        Optional<AgentVersion> latestOpt = getLatestVersion();
        if (!latestOpt.isPresent()) {
            return new VersionCheckResult(false, null, "No versions available");
        }
        
        AgentVersion latest = latestOpt.get();
        
        // 检查平台兼容性
        if (!"ALL".equals(latest.getPlatform()) && !platform.equalsIgnoreCase(latest.getPlatform())) {
            return new VersionCheckResult(false, null, "Platform not compatible");
        }
        
        // 比较版本号
        if (compareVersions(latest.getVersion(), currentVersion) > 0) {
            boolean forceUpgrade = latest.getForceUpgrade() || isForceUpgradeRequired(currentVersion);
            return new VersionCheckResult(true, latest, forceUpgrade ? "Force upgrade required" : "Update available");
        }
        
        return new VersionCheckResult(false, null, "Already up to date");
    }
    
    /**
     * 获取指定平台的所有版本
     */
    public List<AgentVersion> getVersionsByPlatform(String platform) {
        return versionRepository.findByPlatformAndStatus(platform);
    }
    
    /**
     * 获取所有活跃版本
     */
    public List<AgentVersion> getAllActiveVersions() {
        return versionRepository.findByStatusOrderByCreatedAtDesc("ACTIVE");
    }
    
    /**
     * 从上传的文件创建版本
     */
    @Transactional
    public AgentVersion createVersionFromFile(String fileId, String releaseNotes, String createdBy) {
        log.info("Creating agent version from file: {}", fileId);
        
        try {
            // 获取文件信息
            FileDTO fileInfo = getFileInfo(fileId);
            if (fileInfo == null) {
                throw new IllegalArgumentException("File not found: " + fileId);
            }
            
            log.info("File info retrieved: name={}, size={}", fileInfo.getOriginalName(), fileInfo.getFileSize());
            
            // 从文件名解析版本号
            String version = parseVersionFromFilename(fileInfo.getOriginalName());
            if (version == null) {
                throw new IllegalArgumentException("Cannot parse version from filename: " + fileInfo.getOriginalName() + 
                    ". Please ensure filename contains version number like 'agent-1.2.3.jar'");
            }
            
            log.info("Parsed version: {}", version);
            
            // 检查版本是否已存在
            if (versionRepository.findByVersionAndStatus(version, "ACTIVE").isPresent()) {
                throw new IllegalArgumentException("Version already exists: " + version);
            }
            
            // 创建版本记录
            AgentVersion agentVersion = new AgentVersion();
            agentVersion.setVersion(version);
            agentVersion.setFileId(fileId);
            agentVersion.setOriginalFilename(fileInfo.getOriginalName());
            agentVersion.setFileSize(fileInfo.getFileSize());
            agentVersion.setFileHash(fileInfo.getSha256());
            agentVersion.setDownloadUrl("http://localhost:8080/api/web/files/" + fileId + "/download-for-agent");
            agentVersion.setReleaseNotes(releaseNotes);
            agentVersion.setPlatform("ALL"); // 默认支持所有平台
            agentVersion.setForceUpgrade(false); // 默认非强制升级
            agentVersion.setCreatedBy(createdBy);
            agentVersion.setStatus("ACTIVE");
            agentVersion.setIsLatest(false); // 不再使用，保留兼容性
            agentVersion.setIsCurrent(false); // 不再使用，保留兼容性
            
            AgentVersion saved = versionRepository.save(agentVersion);
            log.info("Created agent version {} from file {} by {}", version, fileInfo.getOriginalName(), createdBy);
            return saved;
            
        } catch (Exception e) {
            log.error("Failed to create version from file {}: {}", fileId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 从文件名解析版本号
     * 支持格式：
     * - agent-1.2.3.jar
     * - lightscript-agent-2.0.0.jar
     * - agent-0.1.0-SNAPSHOT.jar
     * - agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar
     */
    private String parseVersionFromFilename(String filename) {
        if (filename == null) return null;
        
        log.debug("Parsing version from filename: {}", filename);
        
        // 移除文件扩展名
        String nameWithoutExt = filename.replaceAll("\\.[^.]+$", "");
        log.debug("Filename without extension: {}", nameWithoutExt);
        
        // 使用更简单的正则表达式，只匹配版本号部分
        // 匹配 x.y.z 或 x.y.z.w 格式的版本号
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+\\.\\d+\\.\\d+(?:\\.\\d+)?)");
        java.util.regex.Matcher matcher = pattern.matcher(nameWithoutExt);
        
        if (matcher.find()) {
            String version = matcher.group(1);
            log.debug("Found version: {}", version);
            return version;
        }
        
        log.warn("Could not parse version from filename: {}", filename);
        return null;
    }
    
    /**
     * 获取文件信息（需要注入FileService或通过API调用）
     */
    private FileDTO getFileInfo(String fileId) {
        // 这里需要调用FileService获取文件信息
        // 为了避免循环依赖，我们通过Repository直接查询
        return fileService.getFileById(fileId);
    }
    
    /**
     * 更新强制升级状态
     */
    @Transactional
    public void updateForceUpgrade(Long id, boolean forceUpgrade) {
        AgentVersion version = versionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Version not found: " + id));
        
        version.setForceUpgrade(forceUpgrade);
        versionRepository.save(version);
        
        log.info("Updated force upgrade for version {}: {}", version.getVersion(), forceUpgrade);
    }
    
    /**
     * 删除版本（软删除）
     */
    @Transactional
    public void deleteVersion(Long id) {
        AgentVersion version = versionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Version not found: " + id));
        
        version.setStatus("DISABLED");
        // 不再需要清除标记，因为基于版本号自动判断
        versionRepository.save(version);
        
        log.info("Disabled agent version: {}", version.getVersion());
    }
    
    /**
     * 比较版本号（语义化版本比较）
     * @param version1 版本1
     * @param version2 版本2
     * @return 正数表示version1更新，负数表示version2更新，0表示相同
     */
    private int compareVersions(String version1, String version2) {
        if (version1 == null && version2 == null) return 0;
        if (version1 == null) return -1;
        if (version2 == null) return 1;
        
        try {
            String[] parts1 = version1.split("\\.");
            String[] parts2 = version2.split("\\.");
            
            int maxLength = Math.max(parts1.length, parts2.length);
            
            for (int i = 0; i < maxLength; i++) {
                int part1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
                int part2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
                
                if (part1 != part2) {
                    return Integer.compare(part1, part2);
                }
            }
            
            return 0; // 版本相同
        } catch (NumberFormatException e) {
            log.warn("Invalid version format: {} or {}, falling back to string comparison", version1, version2);
            return version1.compareTo(version2);
        }
    }
    
    /**
     * 检查是否需要强制升级
     */
    private boolean isForceUpgradeRequired(String currentVersion) {
        List<AgentVersion> forceUpgradeVersions = versionRepository.findByForceUpgradeTrueAndStatus("ACTIVE");
        return forceUpgradeVersions.stream()
            .anyMatch(v -> compareVersions(v.getVersion(), currentVersion) > 0);
    }
    
    /**
     * 版本检查结果
     */
    public static class VersionCheckResult {
        private final boolean updateAvailable;
        private final AgentVersion latestVersion;
        private final String message;
        
        public VersionCheckResult(boolean updateAvailable, AgentVersion latestVersion, String message) {
            this.updateAvailable = updateAvailable;
            this.latestVersion = latestVersion;
            this.message = message;
        }
        
        public boolean isUpdateAvailable() { return updateAvailable; }
        public AgentVersion getLatestVersion() { return latestVersion; }
        public String getMessage() { return message; }
    }
}