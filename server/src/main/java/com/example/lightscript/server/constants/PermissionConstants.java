package com.example.lightscript.server.constants;

import java.util.*;

/**
 * 权限常量定义
 */
public class PermissionConstants {
    
    // 用户管理权限
    public static final String USER_CREATE = "user:create";
    public static final String USER_EDIT = "user:edit";
    public static final String USER_DELETE = "user:delete";
    public static final String USER_VIEW = "user:view";
    
    // 任务管理权限
    public static final String TASK_CREATE = "task:create";
    public static final String TASK_EXECUTE = "task:execute";
    public static final String TASK_DELETE = "task:delete";
    public static final String TASK_VIEW = "task:view";
    
    // 脚本管理权限
    public static final String SCRIPT_CREATE = "script:create";
    public static final String SCRIPT_EDIT = "script:edit";
    public static final String SCRIPT_DELETE = "script:delete";
    public static final String SCRIPT_VIEW = "script:view";
    
    // Agent管理权限
    public static final String AGENT_VIEW = "agent:view";
    public static final String AGENT_GROUP = "agent:group";
    
    // 系统管理权限
    public static final String LOG_VIEW = "log:view";
    public static final String SYSTEM_SETTINGS = "system:settings";
    
    /**
     * 权限信息类
     */
    public static class PermissionInfo {
        private String code;
        private String name;
        private String category;
        private String description;
        
        public PermissionInfo(String code, String name, String category, String description) {
            this.code = code;
            this.name = name;
            this.category = category;
            this.description = description;
        }
        
        public String getCode() { return code; }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public String getDescription() { return description; }
    }
    
    /**
     * 所有权限列表
     */
    public static final List<PermissionInfo> ALL_PERMISSIONS = Arrays.asList(
        // 用户管理
        new PermissionInfo(USER_CREATE, "创建用户", "USER", "可以创建新用户"),
        new PermissionInfo(USER_EDIT, "编辑用户", "USER", "可以编辑用户信息"),
        new PermissionInfo(USER_DELETE, "删除用户", "USER", "可以删除用户"),
        new PermissionInfo(USER_VIEW, "查看用户", "USER", "可以查看用户列表"),
        
        // 任务管理
        new PermissionInfo(TASK_CREATE, "创建任务", "TASK", "可以创建新任务"),
        new PermissionInfo(TASK_EXECUTE, "执行任务", "TASK", "可以启动/停止任务"),
        new PermissionInfo(TASK_DELETE, "删除任务", "TASK", "可以删除任务"),
        new PermissionInfo(TASK_VIEW, "查看任务", "TASK", "可以查看任务列表和详情"),
        
        // 脚本管理
        new PermissionInfo(SCRIPT_CREATE, "创建脚本", "SCRIPT", "可以创建新脚本"),
        new PermissionInfo(SCRIPT_EDIT, "编辑脚本", "SCRIPT", "可以编辑脚本"),
        new PermissionInfo(SCRIPT_DELETE, "删除脚本", "SCRIPT", "可以删除脚本"),
        new PermissionInfo(SCRIPT_VIEW, "查看脚本", "SCRIPT", "可以查看脚本列表"),
        
        // Agent管理
        new PermissionInfo(AGENT_VIEW, "查看Agent", "AGENT", "可以查看Agent列表"),
        new PermissionInfo(AGENT_GROUP, "Agent分组", "AGENT", "可以管理Agent分组"),
        
        // 系统管理
        new PermissionInfo(LOG_VIEW, "查看日志", "SYSTEM", "可以查看执行日志"),
        new PermissionInfo(SYSTEM_SETTINGS, "系统设置", "SYSTEM", "可以修改系统设置")
    );
    
    /**
     * 权限分类
     */
    public static final List<String> CATEGORIES = Arrays.asList("USER", "TASK", "SCRIPT", "AGENT", "SYSTEM");
    
    /**
     * 获取所有权限代码
     */
    public static List<String> getAllPermissionCodes() {
        List<String> codes = new ArrayList<>();
        for (PermissionInfo info : ALL_PERMISSIONS) {
            codes.add(info.getCode());
        }
        return codes;
    }
    
    /**
     * 验证权限代码是否有效
     */
    public static boolean isValidPermission(String permissionCode) {
        return getAllPermissionCodes().contains(permissionCode);
    }
}
