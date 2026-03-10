package com.example.lightscript.server.controller;

import com.example.lightscript.server.entity.SystemSetting;
import com.example.lightscript.server.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/web/system-settings")
@RequiredArgsConstructor
public class SystemSettingController {
    
    private final SystemSettingService systemSettingService;
    
    /**
     * 获取所有系统参数
     */
    @GetMapping
    public ResponseEntity<List<SystemSetting>> getAllSettings() {
        return ResponseEntity.ok(systemSettingService.getAllSettings());
    }
    
    /**
     * 按类别分组获取系统参数
     */
    @GetMapping("/by-category")
    public ResponseEntity<Map<String, List<SystemSetting>>> getSettingsByCategory() {
        return ResponseEntity.ok(systemSettingService.getSettingsByCategory());
    }
    
    /**
     * 根据键获取参数
     */
    @GetMapping("/key/{key}")
    public ResponseEntity<SystemSetting> getSettingByKey(@PathVariable String key) {
        SystemSetting setting = systemSettingService.getSettingByKey(key);
        if (setting == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(setting);
    }
    
    /**
     * 搜索参数
     */
    @GetMapping("/search")
    public ResponseEntity<List<SystemSetting>> searchSettings(@RequestParam String keyword) {
        return ResponseEntity.ok(systemSettingService.searchSettings(keyword));
    }
    
    /**
     * 更新参数值
     */
    @PutMapping("/{id}")
    public ResponseEntity<SystemSetting> updateSetting(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String value = request.get("value");
        SystemSetting updated = systemSettingService.updateSetting(id, value);
        return ResponseEntity.ok(updated);
    }
    
    /**
     * 批量更新参数
     */
    @PutMapping("/batch")
    public ResponseEntity<Void> updateSettings(@RequestBody Map<Long, String> updates) {
        systemSettingService.updateSettings(updates);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 创建新参数
     */
    @PostMapping
    public ResponseEntity<SystemSetting> createSetting(@RequestBody SystemSetting setting) {
        SystemSetting created = systemSettingService.createSetting(setting);
        return ResponseEntity.ok(created);
    }
    
    /**
     * 删除参数
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSetting(@PathVariable Long id) {
        systemSettingService.deleteSetting(id);
        return ResponseEntity.ok().build();
    }
}
