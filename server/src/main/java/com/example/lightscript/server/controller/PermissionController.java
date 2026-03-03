package com.example.lightscript.server.controller;

import com.example.lightscript.server.constants.PermissionConstants;
import com.example.lightscript.server.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/web/permissions")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PermissionController {
    
    private final PermissionService permissionService;
    
    /**
     * 获取所有可用权限
     */
    @GetMapping
    public ResponseEntity<?> getAllPermissions() {
        List<PermissionConstants.PermissionInfo> permissions = permissionService.getAllPermissions();
        List<String> categories = permissionService.getCategories();
        
        Map<String, Object> response = new HashMap<>();
        response.put("permissions", permissions);
        response.put("categories", categories);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取用户权限
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserPermissions(@PathVariable Long userId) {
        List<String> permissions = permissionService.getUserPermissions(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("permissions", permissions);
        return ResponseEntity.ok(response);
    }
}
