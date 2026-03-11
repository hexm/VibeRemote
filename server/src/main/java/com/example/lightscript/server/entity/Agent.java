package com.example.lightscript.server.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "agents", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"hostname", "os_type"}, name = "uk_agent_hostname_ostype")
})
@Data
@EqualsAndHashCode(callSuper = false)
public class Agent {
    
    @Id
    @Column(name = "agent_id", length = 64)
    private String agentId;
    
    @Column(name = "agent_token", length = 64, nullable = false)
    private String agentToken;
    
    @Column(name = "hostname", length = 255, nullable = false)
    private String hostname;
    
    @Column(name = "os_type", length = 20, nullable = false)
    private String osType; // WINDOWS | LINUX
    
    @Column(name = "ip", length = 45)
    private String ip;
    
    @ElementCollection
    @CollectionTable(name = "agent_labels", joinColumns = @JoinColumn(name = "agent_id"))
    @MapKeyColumn(name = "label_key")
    @Column(name = "label_value")
    private Map<String, String> labels;
    
    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;
    
    @Column(name = "status", length = 20)
    private String status = "ONLINE"; // ONLINE | OFFLINE
    
    @Column(name = "cpu_load")
    private Double cpuLoad;
    
    @Column(name = "free_mem_mb")
    private Long freeMemMb;
    
    @Column(name = "total_mem_mb")
    private Long totalMemMb;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "task_count")
    private Integer taskCount = 0; // 任务执行总数
    
    // 扩展字段 - 系统详细信息
    @Column(name = "start_user", length = 100)
    private String startUser; // 启动用户
    
    @Column(name = "working_dir", length = 500)
    private String workingDir; // 工作目录
    
    @Column(name = "disk_space_gb")
    private Long diskSpaceGb; // 磁盘总空间(GB)
    
    @Column(name = "free_space_gb")
    private Long freeSpaceGb; // 磁盘可用空间(GB)
    
    @Column(name = "os_version", length = 200)
    private String osVersion; // 操作系统版本
    
    @Column(name = "java_version", length = 100)
    private String javaVersion; // Java版本
    
    @Column(name = "agent_version", length = 50)
    private String agentVersion; // Agent版本
    
    // 最后一次深度检查任务信息
    @Column(name = "last_diagnostic_task_id", length = 64)
    private String lastDiagnosticTaskId; // 最后一次深度检查任务ID
    
    @Column(name = "last_diagnostic_task_name", length = 200)
    private String lastDiagnosticTaskName; // 最后一次深度检查任务名称
    
    @Column(name = "last_diagnostic_time")
    private LocalDateTime lastDiagnosticTime; // 最后一次深度检查时间
    
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
