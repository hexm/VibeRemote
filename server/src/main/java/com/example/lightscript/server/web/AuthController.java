package com.example.lightscript.server.web;

import com.example.lightscript.server.entity.User;
import com.example.lightscript.server.security.JwtUtil;
import com.example.lightscript.server.service.UserService;
import com.example.lightscript.server.service.WebEncryptionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final WebEncryptionService webEncryptionService;
    
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        Optional<User> userOpt = userService.getUserByUsername(request.getUsername());
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // 检查用户状态和密码
            if ("ACTIVE".equals(user.getStatus()) && 
                org.springframework.security.crypto.bcrypt.BCrypt.checkpw(request.getPassword(), user.getPassword())) {
                
                String token = jwtUtil.generateToken(user.getUsername());
                
                // 更新最后登录时间
                userService.updateLastLoginTime(user.getId());
                
                Map<String, Object> response = new HashMap<>();
                response.put("token", token);
                response.put("username", user.getUsername());
                response.put("email", user.getEmail());
                response.put("realName", user.getRealName());
                response.put("permissions", user.getPermissions());
                // 生成前端通信加密密钥，随登录响应下发
                response.put("encryptionKey", webEncryptionService.generateSessionKey(user.getUsername()));
                
                return ResponseEntity.ok(response);
            }
        }
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Invalid credentials");
        return ResponseEntity.status(401).body(errorResponse);
    }
    
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterUserRequest request) {
        try {
            // 创建普通用户，分配基本权限
            java.util.List<String> basicPermissions = java.util.Arrays.asList(
                "task:view", "script:view", "agent:view", "log:view"
            );
            
            User user = userService.createUser(
                request.getUsername(), 
                request.getPassword(), 
                request.getEmail(), 
                request.getUsername(),  // realName默认使用username
                basicPermissions
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "User created successfully");
            response.put("username", user.getUsername());
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.substring(7); // Remove "Bearer "
            String username = jwtUtil.extractUsername(token);
            
            Optional<User> userOpt = userService.getUserByUsername(username);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                // 验证当前密码
                if (org.springframework.security.crypto.bcrypt.BCrypt.checkpw(request.getCurrentPassword(), user.getPassword())) {
                    userService.resetPassword(user.getId(), request.getNewPassword());
                    Map<String, String> successResponse = new HashMap<>();
                    successResponse.put("message", "Password changed successfully");
                    return ResponseEntity.ok(successResponse);
                } else {
                    Map<String, String> errorResponse = new HashMap<>();
                    errorResponse.put("error", "Current password is incorrect");
                    return ResponseEntity.badRequest().body(errorResponse);
                }
            }
            
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "User not found");
            return ResponseEntity.status(404).body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid token");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    @Data
    public static class LoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }
    
    @Data
    public static class RegisterUserRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
        private String email;
    }
    
    @Data
    public static class ChangePasswordRequest {
        @NotBlank
        private String currentPassword;
        @NotBlank
        private String newPassword;
    }
}
