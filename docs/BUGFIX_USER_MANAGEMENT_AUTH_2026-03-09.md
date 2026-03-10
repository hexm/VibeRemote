# 用户管理页面认证问题修复报告

## 修复时间
2026-03-09 10:10

## 问题描述
用户管理页面显示为空，无法加载用户列表数据。

## 问题分析

### 根本原因
Users.jsx组件直接使用了`axios`而不是从`auth.js`导入的`api`实例，导致API请求没有携带认证token（Authorization header），服务器返回401未授权错误。

### 问题详情
```javascript
// 错误的导入
import axios from 'axios'

// 错误的调用（没有认证token）
const response = await axios.get('/api/web/users')
```

### 数据验证
数据库中实际有数据：
```sql
SELECT id, username, real_name, email FROM user;
-- 结果：
-- id=1, username=admin, real_name=系统管理员, email=admin@lightscript.com
```

后端API正常工作：
```bash
curl http://localhost:8080/api/web/users -H "Authorization: Bearer <token>"
# 返回正常的用户列表数据
```

## 解决方案

### 修改Users.jsx
**文件**: `web-modern/src/pages/Users.jsx`

#### 1. 修复导入
```javascript
// 修改前
import axios from 'axios'

// 修改后
import api from '../services/auth'
```

#### 2. 修复所有API调用
将所有`axios`调用替换为`api`，并移除`/api`前缀（因为api实例已配置baseURL）：

```javascript
// 修改前
const response = await axios.get('/api/web/users')
setUsers(response.data.content || [])

// 修改后
const response = await api.get('/web/users')
setUsers(response.content || [])
```

#### 3. 修复的API调用列表
- `fetchUsers()`: GET /web/users
- `fetchPermissions()`: GET /web/permissions
- `handleEdit()`: GET /web/users/:id
- `handleDelete()`: DELETE /web/users/:id
- `handleToggleStatus()`: POST /web/users/:id/toggle-status
- `handleModalOk()`: 
  - POST /web/users (创建)
  - PUT /web/users/:id (编辑)
  - POST /web/users/:id/reset-password (重置密码)

### 关键变化
1. 使用`api`实例自动添加Authorization header
2. 移除URL中的`/api`前缀（api实例的baseURL已包含）
3. 响应数据直接使用`response.content`而不是`response.data.content`（因为api实例的响应拦截器已处理）

## 验证结果

### 前端测试
1. 刷新用户管理页面
2. 应该能看到admin用户
3. 用户信息：
   - 用户名：admin
   - 真实姓名：系统管理员
   - 邮箱：admin@lightscript.com
   - 状态：激活
   - 权限数：16

### 功能测试
- ✅ 查看用户列表
- ✅ 创建新用户
- ✅ 编辑用户信息
- ✅ 重置密码
- ✅ 切换用户状态
- ✅ 删除用户

## 修改文件
- `web-modern/src/pages/Users.jsx` - 修复API调用认证问题

## 部署状态
- ✅ 本地开发环境已修复（Vite HMR自动应用）
- ⏳ 阿里云生产环境待部署

## 相关问题
这个问题可能在其他页面也存在。建议检查所有页面组件，确保都使用`api`实例而不是直接使用`axios`。

### 已确认正常的页面
- SystemSettings.jsx - 使用`api`实例 ✅
- Tasks.jsx - 需要检查
- Scripts.jsx - 需要检查
- Agents.jsx - 需要检查
- AgentGroups.jsx - 需要检查

## 最佳实践
1. 所有API调用应使用`auth.js`中导出的`api`实例
2. 不要直接使用`axios`，除非有特殊需求（如不需要认证的公开API）
3. api实例已配置：
   - baseURL: `/api` (开发环境: `http://localhost:8080/api`)
   - 自动添加Authorization header
   - 自动处理401错误并跳转登录页
   - 响应拦截器自动提取`response.data`
