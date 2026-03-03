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
        // 创建默认管理员用户（如果不存在）
        if (!userService.getUserByUsername("admin").isPresent()) {
            try {
                // 管理员拥有所有权限
                java.util.List<String> adminPermissions = java.util.Arrays.asList(
                    "user:create", "user:edit", "user:delete", "user:view",
                    "task:create", "task:execute", "task:delete", "task:view",
                    "script:create", "script:edit", "script:delete", "script:view",
                    "agent:view", "agent:group",
                    "log:view", "system:settings"
                );
                
                userService.createUser("admin", "admin123", "admin@lightscript.com", "系统管理员", adminPermissions);
                log.info("Default admin user created: admin/admin123");
            } catch (Exception e) {
                log.error("Failed to create default admin user", e);
            }
        }
    }
}
