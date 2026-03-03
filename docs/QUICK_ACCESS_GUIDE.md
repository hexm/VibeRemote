# LightScript 快速访问指南

## 🌐 访问地址

### 生产环境（阿里云）
- **前端界面**：http://8.138.114.34 或 http://8.138.114.34:3000
- **后端API**：http://8.138.114.34:8080

### 本地开发环境
- **前端界面**：http://localhost:3001
- **后端API**：http://localhost:8080

---

## 🔑 默认账号

### 管理员账号
- **用户名**：`admin`
- **密码**：`admin123`
- **权限**：所有权限（16个）
- **用途**：系统管理、用户管理、完整功能访问

---

## 📱 功能导航

### 1. 仪表盘
- **路径**：`/dashboard`
- **功能**：系统概览、统计数据

### 2. 客户端管理
- **路径**：`/agents`
- **功能**：查看Agent列表、Agent状态

### 3. Agent分组 ⭐ 新功能
- **路径**：`/agent-groups`
- **功能**：
  - 创建分组（业务、环境、地域、自定义）
  - 添加/移除Agent
  - 查看分组详情
  - 管理分组成员

### 4. 任务管理
- **路径**：`/tasks`
- **功能**：
  - 创建任务（手动选择Agent）
  - 按分组创建任务 ⭐ 新功能
  - 查看任务执行状态
  - 查看执行日志

### 5. 脚本管理
- **路径**：`/scripts`
- **功能**：脚本模板管理

### 6. 用户管理 ⭐ 新功能
- **路径**：`/users`
- **功能**：
  - 创建用户
  - 分配权限（16种权限）
  - 快捷权限模板（管理员、操作员、只读）
  - 重置密码
  - 启用/禁用用户

---

## 🚀 快速开始

### 第一次使用

1. **登录系统**
   ```
   访问：http://8.138.114.34
   用户名：admin
   密码：admin123
   ```

2. **创建第一个用户**
   - 点击左侧菜单"用户管理"
   - 点击"创建用户"按钮
   - 填写用户信息
   - 选择"操作员"模板
   - 点击"确定"

3. **创建第一个分组**
   - 点击左侧菜单"Agent分组"
   - 点击"创建分组"按钮
   - 输入分组名称（如：生产环境）
   - 选择分组类型（如：环境分组）
   - 点击"确定"

4. **添加Agent到分组**
   - 在分组列表中点击"查看"
   - 在右侧抽屉中选择Agent
   - Agent自动添加到分组

5. **按分组创建任务**
   - 点击左侧菜单"任务管理"
   - 点击"创建任务"按钮
   - 选择"按分组选择"
   - 选择刚创建的分组
   - 填写脚本内容
   - 点击"创建任务"

---

## 🎯 常用操作

### 用户管理

#### 创建管理员用户
1. 用户管理 → 创建用户
2. 填写用户信息
3. 点击"管理员"模板按钮
4. 确认创建

#### 创建操作员用户
1. 用户管理 → 创建用户
2. 填写用户信息
3. 点击"操作员"模板按钮
4. 确认创建

#### 创建只读用户
1. 用户管理 → 创建用户
2. 填写用户信息
3. 点击"只读"模板按钮
4. 确认创建

### Agent分组

#### 按环境分组
```
分组名称：生产环境
分组类型：环境分组
描述：生产环境服务器
```

#### 按业务分组
```
分组名称：订单系统
分组类型：业务分组
描述：订单系统相关服务器
```

#### 按地域分组
```
分组名称：华东区域
分组类型：地域分组
描述：华东地区服务器
```

### 任务创建

#### 手动选择Agent
1. 任务管理 → 创建任务
2. 选择方式：手动选择
3. 在下拉框中选择多个Agent
4. 填写脚本内容
5. 创建任务

#### 按分组选择Agent
1. 任务管理 → 创建任务
2. 选择方式：按分组选择
3. 选择一个分组
4. Agent列表自动填充
5. 填写脚本内容
6. 创建任务

---

## 🔐 权限说明

### 权限类别

#### 用户管理权限
- `user:create` - 创建用户
- `user:edit` - 编辑用户
- `user:delete` - 删除用户
- `user:view` - 查看用户

#### 任务管理权限
- `task:create` - 创建任务
- `task:execute` - 执行任务
- `task:delete` - 删除任务
- `task:view` - 查看任务

#### 脚本管理权限
- `script:create` - 创建脚本
- `script:edit` - 编辑脚本
- `script:delete` - 删除脚本
- `script:view` - 查看脚本

#### Agent管理权限
- `agent:view` - 查看Agent
- `agent:group` - Agent分组管理

#### 系统管理权限
- `log:view` - 查看日志
- `system:settings` - 系统设置

### 权限模板

#### 管理员模板（16个权限）
- 所有用户管理权限
- 所有任务管理权限
- 所有脚本管理权限
- 所有Agent管理权限
- 所有系统管理权限

#### 操作员模板（11个权限）
- 任务管理：创建、执行、删除、查看
- 脚本管理：创建、编辑、删除、查看
- Agent管理：查看、分组
- 日志查看

#### 只读模板（4个权限）
- 任务查看
- 脚本查看
- Agent查看
- 日志查看

---

## 📊 API文档

### 认证API

#### 登录
```bash
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

### 用户管理API

#### 获取用户列表
```bash
GET /api/web/users?page=0&size=20
Authorization: Bearer {token}
```

#### 创建用户
```bash
POST /api/web/users
Authorization: Bearer {token}
Content-Type: application/json

{
  "username": "testuser",
  "password": "Test1234",
  "email": "test@example.com",
  "realName": "测试用户",
  "permissions": ["task:view", "agent:view"]
}
```

### Agent分组API

#### 获取分组列表
```bash
GET /api/web/agent-groups
Authorization: Bearer {token}
```

#### 创建分组
```bash
POST /api/web/agent-groups
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "生产环境",
  "type": "ENVIRONMENT",
  "description": "生产环境服务器"
}
```

#### 添加Agent到分组
```bash
POST /api/web/agent-groups/{groupId}/agents
Authorization: Bearer {token}
Content-Type: application/json

{
  "agentIds": ["agent-001", "agent-002"]
}
```

---

## 🛠️ 故障排查

### 无法登录
1. 检查用户名和密码是否正确
2. 检查用户状态是否为"启用"
3. 清除浏览器缓存
4. 检查后端服务是否运行

### Agent列表为空
1. 检查是否有Agent服务在运行
2. 检查Agent是否成功注册
3. 访问 `/agents` 页面查看

### 分组列表为空
1. 需要先创建分组
2. 检查是否有`agent:group`权限

### 创建任务失败
1. 检查是否选择了Agent
2. 检查脚本内容是否为空
3. 检查是否有`task:create`权限

---

## 📞 技术支持

### 查看日志
```bash
# 后端日志
ssh root@8.138.114.34 'tail -f /opt/lightscript/backend/backend.log'

# Nginx日志
ssh root@8.138.114.34 'tail -f /opt/lightscript/logs/nginx-access.log'
```

### 重启服务
```bash
# 重启所有服务
ssh root@8.138.114.34 '/opt/lightscript/scripts/restart-all.sh'
```

### 服务状态
```bash
# 检查后端
ssh root@8.138.114.34 'ps aux | grep java | grep server.jar'

# 检查Nginx
ssh root@8.138.114.34 'systemctl status nginx'
```

---

## 📚 相关文档

- [完整实施报告](./IMPLEMENTATION_COMPLETE_USER_AGENT_GROUPS.md)
- [后端实施报告](./USER_MANAGEMENT_AGENT_GROUPS_BACKEND_COMPLETE.md)
- [前端实施报告](./USER_MANAGEMENT_AGENT_GROUPS_FRONTEND_COMPLETE.md)
- [快速测试指南](./USER_MANAGEMENT_QUICK_TEST.md)
- [部署报告](./DEPLOYMENT_USER_AGENT_GROUPS_2026-02-28.md)

---

**文档版本**：v1.0  
**更新日期**：2026-02-28  
**适用版本**：LightScript v0.1.0-SNAPSHOT
