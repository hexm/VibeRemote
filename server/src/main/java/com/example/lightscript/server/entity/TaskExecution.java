package com.example.lightscript.server.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 任务执行实体
 * 代表一个任务在特定代理上的执行实例
 * 一个任务可以有多个执行实例（多个代理或多次重启）
 */
@Entity
@Table(name = "task_executions",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_task_agent_exec",
        columnNames = {"task_id", "agent_id", "execution_number"}
    ),
    indexes = {
        @Index(name = "idx_task_id", columnList = "task_id"),
        @Index(name = "idx_agent_id", columnList = "agent_id"),
        @Index(name = "idx_status", columnList = "status")
    }
)
@Data
@EqualsAndHashCode(callSuper = false)
public class TaskExecution {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @Column(name = "task_id", length = 64, nullable = false)
    private String taskId;
    
    @Column(name = "agent_id", length = 64, nullable = false)
    private String agentId;
    
    @Column(name = "execution_number", nullable = false)
    private Integer executionNumber = 1; // 执行次数，重启时递增
    
    @Column(name = "status", length = 20)
    private String status = "PENDING"; // PENDING | PULLED | RUNNING | SUCCESS | FAILED | TIMEOUT | CANCELLED
    
    @Column(name = "log_file_path", length = 500)
    private String logFilePath; // 日志文件路径
    
    @Column(name = "exit_code")
    private Integer exitCode;
    
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;
    
    @Column(name = "pulled_at")
    private LocalDateTime pulledAt;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    // 文件传输相关字段
    @Column(name = "file_id", length = 50)
    private String fileId; // 传输的文件ID
    
    @Column(name = "target_path", length = 500)
    private String targetPath; // 目标路径
    
    @Column(name = "transfer_size")
    private Long transferSize; // 实际传输大小
    
    @Column(name = "checksum_verified")
    private Boolean checksumVerified; // 校验是否通过
    
    @Column(name = "transfer_speed")
    private Long transferSpeed; // 传输速度（字节/秒）
    
    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails; // 详细错误信息
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
