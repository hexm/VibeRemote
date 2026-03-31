package com.example.lightscript.server.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_log_files", indexes = {
    @Index(name = "idx_agent_log_file_collection", columnList = "collection_id"),
    @Index(name = "idx_agent_log_file_agent", columnList = "agent_id"),
    @Index(name = "idx_agent_log_file_upload_status", columnList = "upload_status")
})
@Data
@EqualsAndHashCode(callSuper = false)
public class AgentLogFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "collection_id", nullable = false)
    private Long collectionId;

    @Column(name = "agent_id", length = 64, nullable = false)
    private String agentId;

    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    @Column(name = "relative_path", length = 1000, nullable = false)
    private String relativePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize = 0L;

    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;

    @Column(name = "upload_status", length = 30, nullable = false)
    private String uploadStatus = "PENDING";

    @Column(name = "uploaded_file_path", length = 1000)
    private String uploadedFilePath;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "upload_task_id", length = 64)
    private String uploadTaskId;

    @Column(name = "upload_execution_id")
    private Long uploadExecutionId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
