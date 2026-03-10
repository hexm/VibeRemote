package com.example.lightscript.server.model;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件管理相关的数据传输对象（DTO）
 */
public class FileModels {

    /**
     * 文件DTO
     */
    @Data
    public static class FileDTO {
        private String fileId;
        private String name;
        private String originalName;
        private String filePath;
        private Long fileSize;
        private String sizeDisplay; // 格式化的文件大小显示
        private String fileType;
        private String category;
        private String version;
        private String md5;
        private String sha256;
        private String description;
        private String tags;
        private String uploadBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    /**
     * 用于任务创建的文件DTO
     */
    @Data
    public static class FileForTaskDTO {
        private String fileId;
        private String name;
        private String originalName;
        private Long fileSize;
        private String sizeDisplay;
        private String category;
        private String md5;
        private String sha256;
    }

    /**
     * 文件上传请求
     */
    @Data
    public static class UploadFileRequest {
        @NotBlank(message = "文件名不能为空")
        private String name;
        
        @NotBlank(message = "文件分类不能为空")
        private String category;
        
        private String description;
        private String tags;
        private String version = "1.0";
    }

    /**
     * 文件列表查询请求
     */
    @Data
    public static class FileListRequest {
        private String keyword; // 搜索关键词
        private String category; // 分类过滤
        private Integer page = 0;
        private Integer size = 10;
    }

    /**
     * 文件传输任务创建请求
     */
    @Data
    public static class CreateFileTransferTaskRequest {
        @NotNull(message = "至少需要选择一个代理")
        private List<String> agentIds;
        
        @NotBlank(message = "任务名称不能为空")
        private String taskName;
        
        @NotBlank(message = "源文件ID不能为空")
        private String sourceFileId;
        
        @NotBlank(message = "目标路径不能为空")
        private String targetPath;
        
        @NotBlank(message = "传输模式不能为空")
        private String transferMode = "OVERWRITE"; // OVERWRITE, BACKUP, VERSION
        
        private Boolean verifyChecksum = true;
        private Integer timeoutSec = 300;
        private String description;
    }

    /**
     * 文件传输配置（存储在Task.scriptContent中的JSON）
     */
    @Data
    public static class FileTransferConfig {
        private String sourceFileId;
        private String targetPath;
        private String transferMode;
        private Boolean verifyChecksum;
        private String description;
    }

    /**
     * 文件传输结果报告
     */
    @Data
    public static class FileTransferResultRequest {
        @NotNull(message = "执行ID不能为空")
        private Long executionId;
        
        @NotBlank(message = "传输状态不能为空")
        private String status; // SUCCESS, FAILED, PARTIAL
        
        private Long transferSize;
        private Boolean checksumVerified;
        private Long transferSpeed;
        private String errorDetails;
    }

    /**
     * 文件校验信息
     */
    @Data
    public static class FileChecksumInfo {
        private String md5;
        private String sha256;
        private Long fileSize;
    }

    /**
     * 文件分类统计
     */
    @Data
    public static class FileCategoryStats {
        private String category;
        private Long count;
        private Long totalSize;
    }

    /**
     * 文件搜索结果
     */
    @Data
    public static class FileSearchResult {
        private String fileId;
        private String name;
        private String category;
        private String sizeDisplay;
        private String uploadBy;
        private LocalDateTime createdAt;
        private String matchType; // NAME, DESCRIPTION, TAGS
    }
}