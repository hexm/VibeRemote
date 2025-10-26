package com.example.lightscript.server.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 批量任务实体
 * 一个批量任务可以包含多个普通任务
 */
@Entity
@Table(name = "batch_tasks")
@Data
@EqualsAndHashCode(callSuper = false)
public class BatchTask {
    
    @Id
    @Column(name = "batch_id", length = 64)
    private String batchId;
    
    @Column(name = "batch_name", length = 200)
    private String batchName;
    
    @Column(name = "script_lang", length = 20)
    private String scriptLang; // bash | powershell | cmd
    
    @Column(name = "script_content", columnDefinition = "TEXT")
    private String scriptContent;
    
    @Column(name = "timeout_sec")
    private Integer timeoutSec;
    
    @Column(name = "target_agent_count")
    private Integer targetAgentCount; // 目标Agent数量
    
    @Column(name = "created_by", length = 64)
    private String createdBy;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
    
    // 统计字段（从关联的Task统计得出）
    @Transient
    private Integer totalTasks = 0;
    
    @Transient
    private Integer pendingTasks = 0;
    
    @Transient
    private Integer runningTasks = 0;
    
    @Transient
    private Integer successTasks = 0;
    
    @Transient
    private Integer failedTasks = 0;
    
    @Transient
    private Integer timeoutTasks = 0;
    
    @Transient
    private Integer cancelledTasks = 0;
    
    @Transient
    private String status; // 综合状态: PENDING | RUNNING | COMPLETED | PARTIAL_FAILED | FAILED
    
    @Transient
    private Double progress; // 完成进度 0-100
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
