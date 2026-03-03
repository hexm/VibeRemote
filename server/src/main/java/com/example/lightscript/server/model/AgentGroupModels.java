package com.example.lightscript.server.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public class AgentGroupModels {
    
    /**
     * 创建分组请求
     */
    @Data
    public static class CreateGroupRequest {
        private String name;
        private String description;
        private String type; // BUSINESS, ENVIRONMENT, REGION, CUSTOM
    }
    
    /**
     * 更新分组请求
     */
    @Data
    public static class UpdateGroupRequest {
        private String name;
        private String description;
    }
    
    /**
     * 添加/移除Agent请求
     */
    @Data
    public static class AgentIdsRequest {
        private List<String> agentIds;
    }
    
    /**
     * 分组DTO
     */
    @Data
    public static class AgentGroupDTO {
        private Long id;
        private String name;
        private String description;
        private String type;
        private Integer agentCount;
        private String createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
    
    /**
     * 分组详情DTO（包含成员列表）
     */
    @Data
    public static class AgentGroupDetailDTO {
        private Long id;
        private String name;
        private String description;
        private String type;
        private List<AgentMemberDTO> agents;
        private String createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
    
    /**
     * 分组成员DTO
     */
    @Data
    public static class AgentMemberDTO {
        private String agentId;
        private String hostname;
        private String status;
        private LocalDateTime addedAt;
    }
    
    /**
     * 简单分组DTO（用于Agent详情中显示所属分组）
     */
    @Data
    public static class SimpleGroupDTO {
        private Long id;
        private String name;
        private String type;
    }
}
