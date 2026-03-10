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
