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
    
    @Column(name = "target_agent_ids", length = 2000)
    private String targetAgentIds; // 目标代理ID列表，逗号分隔
    
    // Transient fields for aggregated status (computed from TaskExecution records)
    @Transient
    private String aggregatedStatus; // ALL_SUCCESS, PARTIAL_SUCCESS, ALL_FAILED, IN_PROGRESS, PENDING
    
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
