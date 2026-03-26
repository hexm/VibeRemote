package com.example.lightscript.server.service;

import com.example.lightscript.server.entity.SystemSetting;
import com.example.lightscript.server.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingService {
    
    private final SystemSettingRepository systemSettingRepository;
    
    /**
     * 获取所有系统参数
     */
    public List<SystemSetting> getAllSettings() {
        return systemSettingRepository.findAll();
    }
    
    /**
     * 按类别分组获取系统参数
     */
    public Map<String, List<SystemSetting>> getSettingsByCategory() {
        return systemSettingRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        setting -> setting.getCategory() != null ? setting.getCategory() : "未分类"
                ));
    }
    
    /**
     * 根据键获取参数
     */
    public SystemSetting getSettingByKey(String key) {
        return systemSettingRepository.findBySettingKey(key)
                .orElse(null);
    }
    
    /**
     * 根据类别获取参数
     */
    public List<SystemSetting> getSettingsByCategory(String category) {
        return systemSettingRepository.findByCategory(category);
    }
    
    /**
     * 搜索参数
     */
    public List<SystemSetting> searchSettings(String keyword) {
        return systemSettingRepository.findBySettingKeyContainingOrDescriptionContaining(keyword, keyword);
    }
    
    /**
     * 更新参数值
     */
    @Transactional
    public SystemSetting updateSetting(Long id, String value) {
        SystemSetting setting = systemSettingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("参数不存在"));
        
        setting.setSettingValue(value);
        return systemSettingRepository.save(setting);
    }
    
    /**
     * 批量更新参数
     */
    @Transactional
    public void updateSettings(Map<Long, String> updates) {
        updates.forEach((id, value) -> {
            systemSettingRepository.findById(id).ifPresent(setting -> {
                setting.setSettingValue(value);
                systemSettingRepository.save(setting);
            });
        });
    }
    
    /**
     * 创建新参数
     */
    @Transactional
    public SystemSetting createSetting(SystemSetting setting) {
        // 检查键是否已存在
        if (systemSettingRepository.findBySettingKey(setting.getSettingKey()).isPresent()) {
            throw new RuntimeException("参数键已存在: " + setting.getSettingKey());
        }
        return systemSettingRepository.save(setting);
    }
    
    /**
     * 删除参数
     */
    @Transactional
    public void deleteSetting(Long id) {
        systemSettingRepository.deleteById(id);
    }
    
    /**
     * 获取参数值（带类型转换）
     */
    public String getSettingValue(String key, String defaultValue) {
        return systemSettingRepository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .orElse(defaultValue);
    }
    
    public Integer getIntValue(String key, Integer defaultValue) {
        try {
            String value = getSettingValue(key, null);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("Failed to parse int value for key: {}", key);
            return defaultValue;
        }
    }
    
    public Boolean getBooleanValue(String key, Boolean defaultValue) {
        String value = getSettingValue(key, null);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    public Long getLongValue(String key, Long defaultValue) {
        try {
            String value = getSettingValue(key, null);
            return value != null ? Long.parseLong(value) : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("Failed to parse long value for key: {}", key);
            return defaultValue;
        }
    }
}
