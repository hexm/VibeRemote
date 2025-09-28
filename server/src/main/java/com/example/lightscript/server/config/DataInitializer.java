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
        // 创建默认管理员用户
        if (!userService.findByUsername("admin").isPresent()) {
            try {
                userService.createUser("admin", "admin123", "admin@lightscript.com", "ADMIN");
                log.info("Default admin user created: admin/admin123");
            } catch (Exception e) {
                log.error("Failed to create default admin user", e);
            }
        }
        
        // 创建默认普通用户
        if (!userService.findByUsername("user").isPresent()) {
            try {
                userService.createUser("user", "user123", "user@lightscript.com", "USER");
                log.info("Default user created: user/user123");
            } catch (Exception e) {
                log.error("Failed to create default user", e);
            }
        }
    }
}
