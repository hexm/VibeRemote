package com.example.lightscript.server.service;

import com.example.lightscript.server.constants.PermissionConstants;
import com.example.lightscript.server.repository.UserPermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionService {
    
    private final UserPermissionRepository userPermissionRepository;
    
    /**
     * 获取所有可用权限
     */
    public List<PermissionConstants.PermissionInfo> getAllPermissions() {
        return PermissionConstants.ALL_PERMISSIONS;
    }
    
    /**
     * 获取权限分类
     */
    public List<String> getCategories() {
        return PermissionConstants.CATEGORIES;
    }
    
    /**
     * 获取用户权限
     */
    public List<String> getUserPermissions(Long userId) {
        return userPermissionRepository.findPermissionCodesByUserId(userId);
    }
    
    /**
     * 检查用户是否有指定权限
     */
    public boolean hasPermission(Long userId, String permissionCode) {
        List<String> permissions = getUserPermissions(userId);
        return permissions.contains(permissionCode);
    }
    
    /**
     * 检查用户是否有任一权限
     */
    public boolean hasAnyPermission(Long userId, String... permissionCodes) {
        List<String> permissions = getUserPermissions(userId);
        for (String code : permissionCodes) {
            if (permissions.contains(code)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查用户是否有所有权限
     */
    public boolean hasAllPermissions(Long userId, String... permissionCodes) {
        List<String> permissions = getUserPermissions(userId);
        for (String code : permissionCodes) {
            if (!permissions.contains(code)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 验证权限代码是否有效
     */
    public boolean isValidPermission(String permissionCode) {
        return PermissionConstants.isValidPermission(permissionCode);
    }
}
