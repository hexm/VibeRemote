# 防止管理员禁用自己功能

## 实现时间
2026-03-09 10:24

## 功能描述
添加安全限制：防止用户禁用自己的账号，避免管理员误操作导致无法登录系统。

## 实现方案

### 后端限制

#### 1. UserController修改
**文件**: `server/src/main/java/com/example/lightscript/server/controller/UserController.java`

添加Authentication参数获取当前登录用户：
```java
@PostMapping("/{userId}/toggle-status")
@RequirePermission("user:edit")
public ResponseEntity<?> toggleUserStatus(@PathVariable Long userId, Authentication authentication) {
    try {
        // 获取当前登录用户
        String currentUsername = authentication.getName();
        
        User user = userService.toggleUserStatus(userId, currentUsername);
        // ...
    }
}
```

#### 2. UserService修改
**文件**: `server/src/main/java/com/example/lightscript/server/service/UserService.java`

添加当前用户检查逻辑：
```java
@Transactional
public User toggleUserStatus(Long userId, String currentUsername) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
    
    // 防止用户禁用自己
    if (user.getUsername().equals(currentUsername) && "ACTIVE".equals(user.getStatus())) {
        throw new IllegalArgumentException("不能禁用自己的账号");
    }
    
    String newStatus = "ACTIVE".equals(user.getStatus()) ? "DISABLED" : "ACTIVE";
    user.setStatus(newStatus);
    user = userRepository.save(user);
    log.info("User status changed: {} -> {}", user.getUsername(), newStatus);
    
    loadUserPermissions(user);
    return user;
}
```

### 前端限制

#### Users.jsx修改
**文件**: `web-modern/src/pages/Users.jsx`

在操作列中禁用当前用户的"禁用"按钮：
```javascript
{
  title: '操作',
  key: 'action',
  render: (_, record) => {
    // 获取当前登录用户
    const currentUser = JSON.parse(localStorage.getItem('userInfo') || '{}')
    const isCurrentUser = currentUser.username === record.username
    
    return (
      <Space>
        {/* ... 其他按钮 ... */}
        <Button
          type="link"
          icon={record.status === 'ACTIVE' ? <StopOutlined /> : <CheckCircleOutlined />}
          onClick={() => handleToggleStatus(record.id)}
          disabled={isCurrentUser && record.status === 'ACTIVE'}
          title={isCurrentUser && record.status === 'ACTIVE' ? '不能禁用自己的账号' : ''}
        >
          {record.status === 'ACTIVE' ? '禁用' : '启用'}
        </Button>
      </Space>
    )
  },
}
```

## 功能特点

### 1. 双重保护
- **前端保护**：禁用按钮，提供友好的用户体验
- **后端保护**：API验证，确保安全性

### 2. 智能判断
- 只在用户尝试禁用自己时阻止
- 允许用户启用自己（如果被其他管理员禁用）
- 允许管理员禁用其他用户

### 3. 友好提示
- 前端：鼠标悬停显示"不能禁用自己的账号"
- 后端：返回明确的错误消息

## 测试场景

### 场景1：管理员尝试禁用自己
1. 以admin身份登录
2. 进入用户管理页面
3. 找到admin用户
4. 观察"禁用"按钮状态
5. **预期**：按钮被禁用，鼠标悬停显示提示

### 场景2：管理员禁用其他用户
1. 以admin身份登录
2. 创建一个新用户test
3. 点击test用户的"禁用"按钮
4. **预期**：成功禁用，显示"用户已禁用"

### 场景3：后端API直接调用
1. 获取admin的token
2. 调用API禁用admin自己
```bash
curl -X POST http://localhost:8080/api/web/users/1/toggle-status \
  -H "Authorization: Bearer <admin-token>"
```
3. **预期**：返回400错误，消息"不能禁用自己的账号"

### 场景4：被禁用的管理员自己启用
1. admin被其他管理员禁用
2. admin无法登录
3. 其他管理员可以启用admin
4. **预期**：admin可以重新登录

## 安全考虑

### 1. 防止误操作
- 管理员不会因为误点击而锁定自己
- 避免需要数据库操作才能恢复

### 2. 多管理员场景
- 如果只有一个管理员，不会被锁定
- 如果有多个管理员，可以互相管理

### 3. 最后一个管理员保护
**建议扩展**：可以进一步添加"最后一个活跃管理员"保护：
```java
// 检查是否是最后一个活跃管理员
long activeAdminCount = userRepository.countByStatusAndHasPermission("ACTIVE", "user:edit");
if (activeAdminCount <= 1 && user.hasPermission("user:edit")) {
    throw new IllegalArgumentException("不能禁用最后一个管理员");
}
```

## 修改文件
- `server/src/main/java/com/example/lightscript/server/controller/UserController.java` - 添加Authentication参数
- `server/src/main/java/com/example/lightscript/server/service/UserService.java` - 添加自我禁用检查
- `web-modern/src/pages/Users.jsx` - 禁用当前用户的禁用按钮

## 部署状态
- ✅ 本地开发环境已实现
- ✅ 前端已通过HMR更新
- ✅ 后端已重新编译启动
- ⏳ 阿里云生产环境待部署

## 相关功能
- 用户状态管理
- 权限控制
- 会话管理

## 最佳实践
1. 重要操作应该有前后端双重验证
2. 前端提供友好的用户体验（禁用按钮+提示）
3. 后端确保安全性（API验证）
4. 错误消息应该清晰明确
