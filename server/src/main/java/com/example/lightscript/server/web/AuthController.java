package com.example.lightscript.server.web;

import com.example.lightscript.server.entity.User;
import com.example.lightscript.server.security.JwtUtil;
import com.example.lightscript.server.service.UserService;
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
    
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        Optional<User> userOpt = userService.findByUsername(request.getUsername());
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getEnabled() && userService.validatePassword(user, request.getPassword())) {
                String token = jwtUtil.generateToken(user.getUsername());
                
                Map<String, Object> response = new HashMap<>();
                response.put("token", token);
                response.put("username", user.getUsername());
                response.put("role", user.getRole());
                response.put("email", user.getEmail());
                
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
            User user = userService.createUser(
                request.getUsername(), 
                request.getPassword(), 
                request.getEmail(), 
                "USER"
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
            
            Optional<User> userOpt = userService.findByUsername(username);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (userService.validatePassword(user, request.getCurrentPassword())) {
                    userService.updatePassword(username, request.getNewPassword());
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
