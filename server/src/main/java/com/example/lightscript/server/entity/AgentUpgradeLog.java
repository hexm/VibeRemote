package com.example.lightscript.server.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_upgrade_logs")
@Data
@EqualsAndHashCode(callSuper = false)
public class AgentUpgradeLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "agent_id", nullable = false, length = 100)
    private String agentId;
    
    @Column(name = "from_version", length = 50)
    private String fromVersion;
    
    @Column(name = "to_version", length = 50)
    private String toVersion;
    
    @Column(name = "upgrade_status", length = 20, nullable = false)
    private String upgradeStatus; // STARTED, DOWNLOADING, INSTALLING, SUCCESS, FAILED, ROLLBACK
    
    @Column(name = "start_time")
    private LocalDateTime startTime;
    
    @Column(name = "end_time")
    private LocalDateTime endTime;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "force_upgrade")
    private Boolean forceUpgrade = false;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }
    }
}