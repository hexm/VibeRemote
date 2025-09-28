package com.example.lightscript.server.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_logs")
@Data
@EqualsAndHashCode(callSuper = false)
public class TaskLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "task_id", length = 64, nullable = false)
    private String taskId;
    
    @Column(name = "seq_num")
    private Integer seqNum;
    
    @Column(name = "stream", length = 10)
    private String stream; // stdout | stderr
    
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "timestamp")
    private LocalDateTime timestamp;
    
    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}
