package com.example.lightscript.server.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public class AgentLogModels {

    @Data
    public static class AgentLogCollectionDTO {
        private Long id;
        private String agentId;
        private String status;
        private String triggeredBy;
        private String errorMessage;
        private String manifestTaskId;
        private Long manifestExecutionId;
        private LocalDateTime createdAt;
        private LocalDateTime finishedAt;
        private Integer totalFiles;
        private Integer successFiles;
        private Integer failedFiles;
        private Integer pendingFiles;
        private List<AgentLogFileDTO> files;
    }

    @Data
    public static class AgentLogFileDTO {
        private Long id;
        private Long collectionId;
        private String agentId;
        private String fileName;
        private String relativePath;
        private Long fileSize;
        private LocalDateTime modifiedAt;
        private String uploadStatus;
        private String uploadedFilePath;
        private LocalDateTime uploadedAt;
        private String uploadTaskId;
        private Long uploadExecutionId;
        private String errorMessage;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
