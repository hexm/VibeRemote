package com.example.lightscript.server.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_versions")
@Data
@EqualsAndHashCode(callSuper = false)
public class AgentVersion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "version", length = 50, nullable = false, unique = true)
    private String version; // 版本号，如 "1.0.0"
    
    @Column(name = "build_number")
    private Integer buildNumber; // 构建号
    
    @Column(name = "release_notes", columnDefinition = "TEXT")
    private String releaseNotes; // 版本更新说明
    
    @Column(name = "download_url", length = 500)
    private String downloadUrl; // 下载地址
    
    @Column(name = "file_id", length = 100)
    private String fileId; // 关联的文件ID
    
    @Column(name = "original_filename", length = 255)
    private String originalFilename; // 原始文件名
    
    @Column(name = "file_size")
    private Long fileSize; // 文件大小（字节）
    
    @Column(name = "file_hash", length = 64)
    private String fileHash; // 文件SHA256哈希值
    
    @Column(name = "is_current")
    private Boolean isCurrent = false; // 是否为当前版本
    
    @Column(name = "is_latest")
    private Boolean isLatest = false; // 是否为最新版本
    
    @Column(name = "min_compatible_version", length = 50)
    private String minCompatibleVersion; // 最小兼容版本
    
    @Column(name = "force_upgrade")
    private Boolean forceUpgrade = false; // 是否强制升级
    
    @Column(name = "platform", length = 20)
    private String platform; // 平台：ALL, WINDOWS, LINUX, MACOS
    
    @Column(name = "status", length = 20)
    private String status = "ACTIVE"; // 状态：ACTIVE, DEPRECATED, DISABLED
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "created_by", length = 100)
    private String createdBy;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}