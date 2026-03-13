package com.example.lightscript.server.config;

import com.example.lightscript.server.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    
    private final UserService userService;
    
    @Override
    public void run(String... args) throws Exception {
        // 管理员拥有所有权限
        java.util.List<String> adminPermissions = java.util.Arrays.asList(
            "user:create", "user:edit", "user:delete", "user:view",
            "task:create", "task:execute", "task:delete", "task:view",
            "script:create", "script:edit", "script:delete", "script:view", "script:list",
            "file:list", "file:view", "file:upload", "file:delete", "file:download",
            "agent:view", "agent:delete", "agent:group",
            "log:view", "system:settings"
        );
        
        // 创建或更新默认管理员用户
        java.util.Optional<com.example.lightscript.server.entity.User> adminUserOpt = userService.getUserByUsername("admin");
        if (!adminUserOpt.isPresent()) {
            try {
                userService.createUser("admin", "admin123", "admin@lightscript.com", "系统管理员", adminPermissions);
                log.info("Default admin user created: admin/admin123");
            } catch (Exception e) {
                log.error("Failed to create default admin user", e);
            }
        } else {
            // 更新现有管理员用户的权限
            try {
                com.example.lightscript.server.entity.User adminUser = adminUserOpt.get();
                userService.updateUser(adminUser.getId(), null, null, adminPermissions);
                log.info("Admin user permissions updated with latest permission set");
            } catch (Exception e) {
                log.error("Failed to update admin user permissions", e);
            }
        }
    }
}
