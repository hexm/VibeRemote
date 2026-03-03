package com.example.lightscript.server.service;

import com.example.lightscript.server.entity.User;
import com.example.lightscript.server.entity.UserPermission;
import com.example.lightscript.server.repository.UserPermissionRepository;
import com.example.lightscript.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final PasswordEncoder passwordEncoder;
    
    /**
     * 获取用户列表（分页）
     */
    public Page<User> getUsers(Pageable pageable) {
        Page<User> userPage = userRepository.findAll(pageable);
        // 加载每个用户的权限
        userPage.getContent().forEach(this::loadUserPermissions);
        return userPage;
    }
    
    /**
     * 按状态获取用户列表
     */
    public Page<User> getUsersByStatus(String status, Pageable pageable) {
        Page<User> userPage = userRepository.findByStatus(status, pageable);
        userPage.getContent().forEach(this::loadUserPermissions);
        return userPage;
    }
    
    /**
     * 搜索用户
     */
    public Page<User> searchUsers(String keyword, Pageable pageable) {
        Page<User> userPage = userRepository.findByUsernameContainingOrRealNameContaining(
            keyword, keyword, pageable);
        userPage.getContent().forEach(this::loadUserPermissions);
        return userPage;
    }
    
    /**
     * 按状态搜索用户
     */
    public Page<User> searchUsersByStatus(String status, String keyword, Pageable pageable) {
        Page<User> userPage = userRepository.findByStatusAndUsernameContainingOrStatusAndRealNameContaining(
            status, keyword, status, keyword, pageable);
        userPage.getContent().forEach(this::loadUserPermissions);
        return userPage;
    }
    
    /**
     * 根据ID获取用户
     */
    public Optional<User> getUserById(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        userOpt.ifPresent(this::loadUserPermissions);
        return userOpt;
    }
    
    /**
     * 根据用户名获取用户
     */
    public Optional<User> getUserByUsername(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        userOpt.ifPresent(this::loadUserPermissions);
        return userOpt;
    }
    
    /**
     * 创建用户
     */
    @Transactional
    public User createUser(String username, String password, String email, 
                          String realName, List<String> permissions) {
        // 验证用户名唯一性
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
        
        // 验证密码强度
        validatePassword(password);
        
        // 创建用户
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setRealName(realName);
        user.setStatus("ACTIVE");
        
        user = userRepository.save(user);
        log.info("User created: {}", username);
        
        // 分配权限
        if (permissions != null && !permissions.isEmpty()) {
            assignPermissions(user.getId(), permissions);
        }
        
        // 加载权限
        loadUserPermissions(user);
        
        return user;
    }
    
    /**
     * 更新用户
     */
    @Transactional
    public User updateUser(Long userId, String email, String realName, List<String> permissions) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        
        // 更新基本信息
        if (email != null) {
            user.setEmail(email);
        }
        if (realName != null) {
            user.setRealName(realName);
        }
        
        user = userRepository.save(user);
        log.info("User updated: {}", user.getUsername());
        
        // 更新权限
        if (permissions != null) {
            // 删除旧权限
            userPermissionRepository.deleteByUserId(userId);
            // 分配新权限
            if (!permissions.isEmpty()) {
                assignPermissions(userId, permissions);
            }
        }
        
        // 加载权限
        loadUserPermissions(user);
        
        return user;
    }
    
    /**
     * 删除用户
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        
        // 删除用户（级联删除权限）
        userRepository.delete(user);
        log.info("User deleted: {}", user.getUsername());
    }
    
    /**
     * 重置密码
     */
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        
        // 验证密码强度
        validatePassword(newPassword);
        
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password reset for user: {}", user.getUsername());
    }
    
    /**
     * 切换用户状态（启用/禁用）
     */
    @Transactional
    public User toggleUserStatus(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        
        String newStatus = "ACTIVE".equals(user.getStatus()) ? "DISABLED" : "ACTIVE";
        user.setStatus(newStatus);
        user = userRepository.save(user);
        log.info("User status changed: {} -> {}", user.getUsername(), newStatus);
        
        loadUserPermissions(user);
        return user;
    }
    
    /**
     * 更新最后登录时间
     */
    @Transactional
    public void updateLastLoginTime(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        }
    }
    
    /**
     * 分配权限
     */
    private void assignPermissions(Long userId, List<String> permissions) {
        for (String permissionCode : permissions) {
            UserPermission up = new UserPermission();
            up.setUserId(userId);
            up.setPermissionCode(permissionCode);
            userPermissionRepository.save(up);
        }
        log.info("Assigned {} permissions to user {}", permissions.size(), userId);
    }
    
    /**
     * 加载用户权限
     */
    private void loadUserPermissions(User user) {
        List<String> permissions = userPermissionRepository.findPermissionCodesByUserId(user.getId());
        user.setPermissions(permissions);
    }
    
    /**
     * 验证密码强度
     */
    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("密码长度至少8位");
        }
        
        boolean hasLetter = password.matches(".*[a-zA-Z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException("密码必须包含字母和数字");
        }
    }
}
