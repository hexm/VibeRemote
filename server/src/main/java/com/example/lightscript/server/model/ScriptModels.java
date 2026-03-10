package com.example.lightscript.server.model;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

public class ScriptModels {
    
    @Data
    public static class ScriptDTO {
        private Long id;
        private String scriptId;
        private String name;
        private String filename;
        private String type;
        private String description;
        private String content;
        private String filePath;
        private Long fileSize;
        private String encoding;
        private Boolean isUploaded;
        private Integer usageCount;
        private String createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        
        // 计算属性
        private String sizeDisplay; // 格式化的文件大小显示
    }
    
    @Data
    public static class CreateScriptRequest {
        @NotBlank(message = "脚本名称不能为空")
        @Size(max = 255, message = "脚本名称长度不能超过255个字符")
        private String name;
        
        @NotBlank(message = "文件名不能为空")
        @Size(max = 255, message = "文件名长度不能超过255个字符")
        private String filename;
        
        @NotBlank(message = "脚本类型不能为空")
        private String type;
        
        @Size(max = 1000, message = "脚本描述长度不能超过1000个字符")
        private String description;
        
        @NotBlank(message = "脚本内容不能为空")
        private String content;
        
        private String encoding = "UTF-8";
    }
    
    @Data
    public static class UpdateScriptRequest {
        @NotBlank(message = "脚本名称不能为空")
        @Size(max = 255, message = "脚本名称长度不能超过255个字符")
        private String name;
        
        @NotBlank(message = "文件名不能为空")
        @Size(max = 255, message = "文件名长度不能超过255个字符")
        private String filename;
        
        @NotBlank(message = "脚本类型不能为空")
        private String type;
        
        @Size(max = 1000, message = "脚本描述长度不能超过1000个字符")
        private String description;
        
        @NotBlank(message = "脚本内容不能为空")
        private String content;
        
        private String encoding = "UTF-8";
    }
    
    @Data
    public static class UploadScriptRequest {
        @NotBlank(message = "脚本名称不能为空")
        @Size(max = 255, message = "脚本名称长度不能超过255个字符")
        private String name;
        
        @NotBlank(message = "脚本类型不能为空")
        private String type;
        
        @Size(max = 1000, message = "脚本描述长度不能超过1000个字符")
        private String description;
        
        // 文件信息由MultipartFile提供
    }
    
    @Data
    public static class ScriptListRequest {
        private String keyword; // 搜索关键词
        private String type;    // 脚本类型过滤
        private Integer page = 0;
        private Integer size = 10;
    }
    
    @Data
    public static class ScriptForTaskDTO {
        private String scriptId;
        private String name;
        private String type;
        private String content;
        private String filename;
        private Boolean isUploaded;
    }
}