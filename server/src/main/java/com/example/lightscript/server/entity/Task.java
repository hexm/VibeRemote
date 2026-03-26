package com.example.lightscript.server.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "tasks")
@Data
@EqualsAndHashCode(callSuper = false)
public class Task {
    
    @Id
    @Column(name = "task_id", length = 64)
    private String taskId;
    
    @Column(name = "task_name", length = 200)
    private String taskName; // 任务名称
    
    @Column(name = "script_lang", length = 20)
    private String scriptLang; // bash | powershell | cmd
    
    @Column(name = "script_content", columnDefinition = "TEXT")
    private String scriptContent;
    
    @Column(name = "timeout_sec")
    private Integer timeoutSec;
    
    @ElementCollection
    @CollectionTable(name = "task_env", joinColumns = @JoinColumn(name = "task_id"))
    @MapKeyColumn(name = "env_key")
    @Column(name = "env_value")
    private Map<String, String> env;
    
    @Column(name = "created_by", length = 64)
    private String createdBy;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "task_status", length = 20)
    private String taskStatus; // DRAFT | PENDING | RUNNING | SUCCESS | FAILED | PARTIAL_SUCCESS | STOPPED | CANCELLED
    
    @Column(name = "task_type", length = 20)
    private String taskType = "SCRIPT"; // SCRIPT | FILE_TRANSFER | FILE_UPLOAD
    
    @Column(name = "target_agent_ids", length = 2000)
    private String targetAgentIds; // 目标代理ID列表，逗号分隔
    
    @Column(name = "execution_count")
    private Integer executionCount = 1; // 任务执行次数，重启时递增
    
    @Column(name = "started_at")
    private LocalDateTime startedAt; // 任务开始时间（首次执行或重启时更新）
    
    @Column(name = "finished_at")
    private LocalDateTime finishedAt; // 任务结束时间
    
    // 文件传输任务配置字段
    @Column(name = "overwrite_existing")
    private Boolean overwriteExisting; // 是否覆盖已存在的文件
    
    @Column(name = "verify_checksum")
    private Boolean verifyChecksum; // 是否验证校验和
    
    // Transient fields for display purposes (computed from TaskExecution records)
    @Transient
    private Integer targetAgentCount; // 目标代理数量
    
    @Transient
    private Integer completedExecutions; // 已完成的执行数
    
    @Transient
    private String executionProgress; // 执行进度，如 "3/5"
    
    @Transient
    private Integer pendingCount;
    
    @Transient
    private Integer runningCount;
    
    @Transient
    private Integer successCount;
    
    @Transient
    private Integer failedCount;
    
    @Transient
    private Integer timeoutCount;
    
    @Transient
    private Integer cancelledCount;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
