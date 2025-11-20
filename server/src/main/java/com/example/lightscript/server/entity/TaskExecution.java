package com.example.lightscript.server.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 任务执行历史记录
 */
@Entity
@Table(name = "task_executions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "execution_seq"})
)
@Data
public class TaskExecution {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 任务ID（关联Task表）
     */
    @Column(name = "task_id", length = 64, nullable = false)
    private String taskId;
    
    /**
     * 执行序号：1, 2, 3...
     */
    @Column(name = "execution_seq", nullable = false)
    private Integer executionSeq;
    
    /**
     * 执行状态：SUCCESS/FAILED/TIMEOUT
     */
    @Column(name = "status", length = 20)
    private String status;
    
    /**
     * 退出码
     */
    @Column(name = "exit_code")
    private Integer exitCode;
    
    /**
     * 启动时间
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    /**
     * 完成时间
     */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
    
    /**
     * 执行时长（毫秒）
     */
    @Column(name = "duration_ms")
    private Long durationMs;
    
    /**
     * 执行摘要
     */
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;
    
    /**
     * 日志文件路径
     */
    @Column(name = "log_file_path", length = 500)
    private String logFilePath;
    
    /**
     * 日志文件大小（字节）
     */
    @Column(name = "log_size_bytes")
    private Long logSizeBytes;
    
    /**
     * 记录创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
