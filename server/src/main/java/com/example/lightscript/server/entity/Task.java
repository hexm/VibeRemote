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
    
    @Column(name = "agent_id", length = 64, nullable = false)
    private String agentId;
    
    @Column(name = "batch_id", length = 64)
    private String batchId; // 所属批量任务ID，null表示普通任务
    
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
    
    @Column(name = "status", length = 20)
    private String status = "PENDING"; // PENDING | PULLED | RUNNING | SUCCESS | FAILED | TIMEOUT | CANCELLED
    
    @Column(name = "execution_count")
    private Integer executionCount = 0; // 执行次数，每次启动时累加
    
    @Column(name = "log_file_path", length = 500)
    private String logFilePath; // 日志文件路径
    
    @Column(name = "exit_code")
    private Integer exitCode;
    
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;
    
    @Column(name = "created_by", length = 64)
    private String createdBy;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "pulled_at")
    private LocalDateTime pulledAt;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
