package com.example.lightscript.server.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "files", indexes = {
    @Index(name = "idx_file_id", columnList = "file_id"),
    @Index(name = "idx_category", columnList = "category"),
    @Index(name = "idx_upload_by", columnList = "upload_by"),
    @Index(name = "idx_md5", columnList = "md5"),
    @Index(name = "idx_sha256", columnList = "sha256")
})
@Data
@EqualsAndHashCode(callSuper = false)
public class File {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "file_id", length = 50, nullable = false, unique = true)
    private String fileId;
    
    @Column(name = "name", length = 255, nullable = false)
    private String name;
    
    @Column(name = "original_name", length = 255, nullable = false)
    private String originalName;
    
    @Column(name = "file_path", length = 500, nullable = false)
    private String filePath;
    
    @Column(name = "file_size", nullable = false)
    private Long fileSize;
    
    @Column(name = "file_type", length = 100)
    private String fileType;
    
    @Column(name = "category", length = 50)
    private String category;
    
    @Column(name = "version", length = 20)
    private String version = "1.0";
    
    @Column(name = "md5", length = 32)
    private String md5;
    
    @Column(name = "sha256", length = 64)
    private String sha256;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "tags", length = 500)
    private String tags;
    
    @Column(name = "upload_by", length = 100, nullable = false)
    private String uploadBy;
    
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