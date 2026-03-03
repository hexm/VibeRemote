# 权限控制实现文档

## 实施时间
2026-03-02 14:30

## 问题描述
系统缺少权限控制,任何登录用户都可以:
- 创建、编辑、删除其他用户
- 修改管理员密码
- 禁用/启用用户
- 访问所有功能

这是一个严重的安全漏洞。

## 解决方案

### 1. 启用Spring Security
恢复SecurityConfig的注解:
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
```

### 2. 创建权限检查注解
`@RequirePermission` - 用于标记需要特定权限的方法

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String value(); // 权限代码,如 "user:edit"
}
```

### 3. 实现权限检查切面
`PermissionAspect` - 使用AOP拦截带有@RequirePermission注解的方法

检查逻辑:
1. 验证用户是否已登录
2. 检查用户状态是否为ACTIVE
3. 验证用户是否拥有所需权限
4. 权限不足时抛出SecurityException

### 4. 在Controller中添加权限注解

#### UserController权限要求:
- `GET /api/web/users` - 需要 `user:view` 权限
- `GET /api/web/users/{id}` - 需要 `user:view` 权限
- `POST /api/web/users` - 需要 `user:create` 权限
- `PUT /api/web/users/{id}` - 需要 `user:edit` 权限
- `DELETE /api/web/users/{id}` - 需要 `user:delete` 权限
- `POST /api/web/users/{id}/reset-password` - 需要 `user:edit` 权限
- `POST /api/web/users/{id}/toggle-status` - 需要 `user:edit` 权限

### 5. 全局异常处理
`GlobalExceptionHandler` - 统一处理权限异常

- SecurityException → 403 Forbidden
- IllegalArgumentException → 400 Bad Request
- Exception → 500 Internal Server Error

### 6. 前端权限错误处理
创建axios拦截器,处理403错误:
- 显示"权限不足"提示
- 401错误时自动跳转登录页

## 权限列表

### 用户管理权限
- `user:view` - 查看用户
- `user:create` - 创建用户
- `user:edit` - 编辑用户
- `user:delete` - 删除用户

### 任务管理权限
- `task:view` - 查看任务
- `task:create` - 创建任务
- `task:execute` - 执行任务
- `task:delete` - 删除任务

### 脚本管理权限
- `script:view` - 查看脚本
- `script:create` - 创建脚本
- `script:edit` - 编辑脚本
- `script:delete` - 删除脚本

### Agent管理权限
- `agent:view` - 查看Agent
- `agent:group` - 管理Agent分组

### 系统权限
- `log:view` - 查看日志
- `system:settings` - 系统设置

## 权限模板

### 管理员 (16个权限)
拥有所有权限

### 操作员 (11个权限)
- 任务: create, execute, delete, view
- 脚本: create, edit, delete, view
- Agent: view, group
- 日志: view

### 只读 (4个权限)
- 任务: view
- 脚本: view
- Agent: view
- 日志: view

## 测试场景

### 场景1: 普通用户尝试创建用户
1. 创建一个只有`task:view`权限的用户
2. 登录该用户
3. 尝试访问用户管理页面
4. **预期结果**: 可以看到页面,但点击"创建用户"时返回403错误

### 场景2: 操作员尝试删除用户
1. 创建一个操作员用户(11个权限,不包含user:delete)
2. 登录该用户
3. 尝试删除其他用户
4. **预期结果**: 返回403错误,"权限不足: 需要 user:delete 权限"

### 场景3: 管理员正常操作
1. 使用admin账号登录
2. 执行所有操作
3. **预期结果**: 所有操作正常

### 场景4: 禁用用户尝试访问
1. 创建用户并禁用
2. 使用该用户登录
3. 尝试访问任何API
4. **预期结果**: 返回403错误,"用户已被禁用"

## 技术实现

### 依赖
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

### 核心文件
- `RequirePermission.java` - 权限注解
- `PermissionAspect.java` - 权限检查切面
- `GlobalExceptionHandler.java` - 全局异常处理
- `SecurityConfig.java` - Spring Security配置
- `UserController.java` - 添加权限注解
- `web-modern/src/utils/axios.js` - 前端拦截器

## 安全建议

1. **最小权限原则**: 只给用户必需的权限
2. **定期审计**: 定期检查用户权限分配
3. **密码策略**: 强制8位以上,包含字母和数字
4. **会话管理**: JWT token有效期设置合理
5. **日志记录**: 记录所有权限检查失败的尝试

## 部署信息
- **服务器**: 8.138.114.34
- **部署时间**: 2026-03-02 14:32
- **后端版本**: server-0.1.0-SNAPSHOT.jar
- **前端版本**: index-FWHGeZYn.js

## 验证步骤

1. 使用admin登录,创建一个只有`task:view`权限的测试用户
2. 退出登录,使用测试用户登录
3. 尝试访问用户管理页面
4. 尝试创建用户 → 应该返回403错误
5. 尝试编辑用户 → 应该返回403错误
6. 尝试删除用户 → 应该返回403错误

## 状态
✅ **已实施并部署**

## 后续优化

1. 前端根据用户权限隐藏无权限的按钮
2. 添加权限管理页面,可视化查看所有权限
3. 支持权限组/角色(当前是直接权限绑定)
4. 添加操作审计日志
5. 支持更细粒度的权限控制(如只能编辑自己创建的任务)
