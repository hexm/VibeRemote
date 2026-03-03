package com.example.lightscript.server.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public class UserModels {
    
    /**
     * 创建用户请求
     */
    @Data
    public static class CreateUserRequest {
        private String username;
        private String password;
        private String email;
        private String realName;
        private List<String> permissions;
    }
    
    /**
     * 更新用户请求
     */
    @Data
    public static class UpdateUserRequest {
        private String email;
        private String realName;
        private List<String> permissions;
    }
    
    /**
     * 重置密码请求
     */
    @Data
    public static class ResetPasswordRequest {
        private String newPassword;
    }
    
    /**
     * 用户响应DTO
     */
    @Data
    public static class UserDTO {
        private Long id;
        private String username;
        private String email;
        private String realName;
        private String status;
        private List<String> permissions;
        private Integer permissionCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime lastLoginAt;
    }
    
    /**
     * 用户响应（简化版，不含权限列表）
     */
    @Data
    public static class UserSimpleDTO {
        private Long id;
        private String username;
        private String email;
        private String realName;
        private String status;
        private Integer permissionCount;
        private LocalDateTime createdAt;
        private LocalDateTime lastLoginAt;
    }
}
