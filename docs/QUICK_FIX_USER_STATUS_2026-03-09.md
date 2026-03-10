# 用户状态修复 - 快速指南

## 问题
前端报错"获取用户列表失败"

## 根本原因
数据库中的admin用户状态为`DISABLED`（禁用），导致无法登录，前端使用的旧token已失效。

## 解决方案

### 1. 启用admin用户（已完成）
```sql
UPDATE user SET status='ACTIVE' WHERE username='admin';
```

### 2. 前端清除旧token并重新登录

#### 方法1：清除浏览器缓存
1. 打开浏览器开发者工具 (F12)
2. 进入 Application → Local Storage → http://localhost:3001
3. 删除以下项：
   - `token`
   - `user`
   - `userInfo`
4. 刷新页面
5. 重新登录（admin/admin123）

#### 方法2：直接刷新页面
由于我们已经修复了会话过期自动跳转功能，你也可以：
1. 直接刷新页面
2. 系统会检测到token无效
3. 自动跳转到登录页
4. 重新登录（admin/admin123）

## 验证
登录后，访问"用户管理"页面，应该能看到：
- 用户名：admin
- 真实姓名：系统管理员
- 邮箱：admin@lightscript.com
- 状态：激活
- 权限数：16

## 为什么admin用户被禁用？
可能的原因：
1. 数据库迁移时的默认状态设置
2. 之前的测试操作
3. 数据初始化脚本的问题

## 预防措施
建议在DataInitializer中确保admin用户始终为ACTIVE状态：

```java
@PostConstruct
public void init() {
    User admin = userRepository.findByUsername("admin").orElse(null);
    if (admin != null && admin.getStatus() == UserStatus.DISABLED) {
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.save(admin);
        log.info("Admin user status updated to ACTIVE");
    }
}
```

## 测试API
```bash
# 1. 登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 2. 获取用户列表（使用返回的token）
curl http://localhost:8080/api/web/users \
  -H "Authorization: Bearer <your-token>"
```

## 状态
- ✅ 数据库admin用户已启用
- ✅ 后端API正常工作
- ⏳ 前端需要清除旧token并重新登录
