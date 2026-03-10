package com.example.lightscript.server.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "scripts")
@EqualsAndHashCode(callSuper = false)
public class Script {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "script_id", unique = true, nullable = false, length = 50)
    private String scriptId;
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "filename", nullable = false)
    private String filename;
    
    @Column(name = "type", nullable = false, length = 50)
    private String type;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;
    
    @Column(name = "file_path", length = 500)
    private String filePath;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "encoding", length = 50)
    private String encoding = "UTF-8";
    
    @Column(name = "is_uploaded")
    private Boolean isUploaded = false;
    
    @Column(name = "usage_count")
    private Integer usageCount = 0;
    
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
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