package com.example.lightscript.server.config;

import com.example.lightscript.server.constants.PermissionConstants;
import com.example.lightscript.server.entity.SystemSetting;
import com.example.lightscript.server.entity.User;
import com.example.lightscript.server.service.SystemSettingService;
import com.example.lightscript.server.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    
    private final UserService userService;
    private final SystemSettingService systemSettingService;
    
    @Override
    public void run(String... args) throws Exception {
        List<String> defaultAdminPermissions = PermissionConstants.getAllPermissionCodes();
        
        // 创建或更新默认管理员用户
        Optional<User> adminUserOpt = userService.getUserByUsername("admin");
        if (!adminUserOpt.isPresent()) {
            try {
                userService.createUser("admin", "admin123", "admin@lightscript.com", "系统管理员", defaultAdminPermissions);
                log.info("Default admin user created: admin/admin123");
            } catch (Exception e) {
                log.error("Failed to create default admin user", e);
            }
        } else {
            try {
                User adminUser = adminUserOpt.get();
                Set<String> mergedPermissions = new LinkedHashSet<>(adminUser.getPermissions());
                mergedPermissions.addAll(defaultAdminPermissions);
                List<String> updatedPermissions = new ArrayList<>(mergedPermissions);

                if (!samePermissions(adminUser.getPermissions(), updatedPermissions)) {
                    userService.updateUser(adminUser.getId(), null, null, updatedPermissions);
                    log.info("Admin user permissions merged with latest default permission set");
                } else {
                    log.info("Admin user permissions already include all default permissions");
                }
            } catch (Exception e) {
                log.error("Failed to update admin user permissions", e);
            }
        }

        ensureSystemSetting(
            "task.file_upload.max_size_mb",
            "500",
            "NUMBER",
            "task",
            "文件上传任务允许的最大压缩包大小（MB），Agent打包后和服务端接收时都会校验"
        );

        ensureSystemSetting(
            "agent.screen_monitor.enabled",
            "true",
            "BOOLEAN",
            "agent",
            "是否启用 Agent 屏幕监控功能。关闭后前端隐藏入口，服务端拒绝新的屏幕监控连接"
        );

        ensureSystemSetting(
            "agent.upgrade.public_base_url",
            "",
            "STRING",
            "agent",
            "Agent 自动升级下载地址的公共访问前缀。为空时使用服务端配置 lightscript.agent.public-base-url"
        );
    }

    private void ensureSystemSetting(String key, String value, String type, String category, String description) {
        if (systemSettingService.getSettingByKey(key) != null) {
            return;
        }

        try {
            SystemSetting setting = new SystemSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            setting.setSettingType(type);
            setting.setCategory(category);
            setting.setDescription(description);
            systemSettingService.createSetting(setting);
            log.info("Default system setting created: {}={}", key, value);
        } catch (Exception e) {
            log.error("Failed to create default system setting: {}", key, e);
        }
    }

    private boolean samePermissions(List<String> existingPermissions, List<String> updatedPermissions) {
        return new LinkedHashSet<>(existingPermissions).equals(new LinkedHashSet<>(updatedPermissions));
    }
}
