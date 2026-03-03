package com.example.lightscript.server.controller;

import com.example.lightscript.server.entity.User;
import com.example.lightscript.server.model.UserModels.*;
import com.example.lightscript.server.security.RequirePermission;
import com.example.lightscript.server.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/web/users")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class UserController {
    
    private final UserService userService;
    
    /**
     * 获取用户列表
     */
    @GetMapping
    @RequirePermission("user:view")
    public ResponseEntity<?> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> userPage;
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            if (status != null && !status.trim().isEmpty()) {
                userPage = userService.searchUsersByStatus(status, keyword, pageable);
            } else {
                userPage = userService.searchUsers(keyword, pageable);
            }
        } else if (status != null && !status.trim().isEmpty()) {
            userPage = userService.getUsersByStatus(status, pageable);
        } else {
            userPage = userService.getUsers(pageable);
        }
        
        // 转换为DTO
        Page<UserSimpleDTO> dtoPage = userPage.map(this::toUserSimpleDTO);
        
        return ResponseEntity.ok(dtoPage);
    }
    
    /**
     * 获取用户详情
     */
    @GetMapping("/{userId}")
    @RequirePermission("user:view")
    public ResponseEntity<?> getUserById(@PathVariable Long userId) {
        User user = userService.getUserById(userId)
            .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        
        return ResponseEntity.ok(toUserDTO(user));
    }
    
    /**
     * 创建用户
     */
    @PostMapping
    @RequirePermission("user:create")
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        try {
            User user = userService.createUser(
                request.getUsername(),
                request.getPassword(),
                request.getEmail(),
                request.getRealName(),
                request.getPermissions()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", user.getId());
            response.put("username", user.getUsername());
            response.put("message", "用户创建成功");
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 更新用户
     */
    @PutMapping("/{userId}")
    @RequirePermission("user:edit")
    public ResponseEntity<?> updateUser(
            @PathVariable Long userId,
            @RequestBody UpdateUserRequest request) {
        try {
            User user = userService.updateUser(
                userId,
                request.getEmail(),
                request.getRealName(),
                request.getPermissions()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "用户更新成功");
            response.put("user", toUserDTO(user));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 删除用户
     */
    @DeleteMapping("/{userId}")
    @RequirePermission("user:delete")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId) {
        try {
            userService.deleteUser(userId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "用户删除成功");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 重置密码
     */
    @PostMapping("/{userId}/reset-password")
    @RequirePermission("user:edit")
    public ResponseEntity<?> resetPassword(
            @PathVariable Long userId,
            @RequestBody ResetPasswordRequest request) {
        try {
            userService.resetPassword(userId, request.getNewPassword());
            Map<String, String> response = new HashMap<>();
            response.put("message", "密码重置成功");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 切换用户状态
     */
    @PostMapping("/{userId}/toggle-status")
    @RequirePermission("user:edit")
    public ResponseEntity<?> toggleUserStatus(@PathVariable Long userId) {
        try {
            User user = userService.toggleUserStatus(userId);
            Map<String, Object> response = new HashMap<>();
            response.put("status", user.getStatus());
            response.put("message", "ACTIVE".equals(user.getStatus()) ? "用户已启用" : "用户已禁用");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 转换为UserDTO
     */
    private UserDTO toUserDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRealName(user.getRealName());
        dto.setStatus(user.getStatus());
        dto.setPermissions(user.getPermissions());
        dto.setPermissionCount(user.getPermissions().size());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        dto.setLastLoginAt(user.getLastLoginAt());
        return dto;
    }
    
    /**
     * 转换为UserSimpleDTO
     */
    private UserSimpleDTO toUserSimpleDTO(User user) {
        UserSimpleDTO dto = new UserSimpleDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRealName(user.getRealName());
        dto.setStatus(user.getStatus());
        dto.setPermissionCount(user.getPermissions().size());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setLastLoginAt(user.getLastLoginAt());
        return dto;
    }
}
