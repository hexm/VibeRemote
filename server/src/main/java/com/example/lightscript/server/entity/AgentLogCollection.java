package com.example.lightscript.server.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_log_collections", indexes = {
    @Index(name = "idx_agent_log_collection_agent", columnList = "agent_id"),
    @Index(name = "idx_agent_log_collection_status", columnList = "status")
})
@Data
@EqualsAndHashCode(callSuper = false)
public class AgentLogCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", length = 64, nullable = false)
    private String agentId;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "COLLECTING";

    @Column(name = "triggered_by", length = 64)
    private String triggeredBy;

    @Column(name = "manifest_task_id", length = 64)
    private String manifestTaskId;

    @Column(name = "manifest_execution_id")
    private Long manifestExecutionId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
