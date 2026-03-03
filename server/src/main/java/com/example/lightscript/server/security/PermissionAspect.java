package com.example.lightscript.server.security;

import com.example.lightscript.server.entity.User;
import com.example.lightscript.server.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 权限检查切面
 * 拦截带有@RequirePermission注解的方法,检查当前用户是否有相应权限
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionAspect {
    
    private final UserService userService;
    
    @Around("@annotation(com.example.lightscript.server.security.RequirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // 获取注解
        RequirePermission requirePermission = method.getAnnotation(RequirePermission.class);
        String requiredPermission = requirePermission.value();
        
        // 获取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("未认证用户尝试访问需要权限的接口: {}", requiredPermission);
            throw new SecurityException("未登录或登录已过期");
        }
        
        String username = authentication.getName();
        User user = userService.getUserByUsername(username)
            .orElseThrow(() -> new SecurityException("用户不存在"));
        
        // 检查用户状态
        if (!"ACTIVE".equals(user.getStatus())) {
            log.warn("已禁用用户尝试访问接口: username={}, permission={}", username, requiredPermission);
            throw new SecurityException("用户已被禁用");
        }
        
        // 检查权限
        if (!user.getPermissions().contains(requiredPermission)) {
            log.warn("用户权限不足: username={}, required={}, has={}", 
                username, requiredPermission, user.getPermissions());
            throw new SecurityException("权限不足: 需要 " + requiredPermission + " 权限");
        }
        
        log.debug("权限检查通过: username={}, permission={}", username, requiredPermission);
        
        // 执行原方法
        return joinPoint.proceed();
    }
}
