# 用户管理和客户端分组功能 - 设计文档

## 1. 系统架构设计

### 1.1 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                      前端层 (React)                      │
│  ┌──────────────┐  ┌──────────────┐                     │
│  │ 用户管理页面  │  │ 分组管理页面  │                     │
│  └──────────────┘  └──────────────┘                     │
└─────────────────────────────────────────────────────────┘
                            │
                    RESTful API
                            │
┌─────────────────────────────────────────────────────────┐
│                    后端层 (Spring Boot)                  │
│  ┌──────────────┐  ┌──────────────┐                     │
│  │ UserService  │  │ GroupService │                     │
│  └──────────────┘  └──────────────┘                     │
│  ┌──────────────────────────────────────────────────┐  │
│  │         Spring Security + 权限拦截器              │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────┐
│                    数据层 (MySQL/H2)                     │
│  User, UserPermission, AgentGroup, AgentGroupMember     │
└─────────────────────────────────────────────────────────┘
```

### 1.2 权限验证流程

```
用户请求 → JWT验证 → 获取用户ID → 查询用户权限 → 
检查权限代码 → 允许/拒绝访问
```

## 2. 数据库设计

### 2.1 ER图

```
User ──── UserPermission (存储权限代码字符串)

AgentGroup ──── AgentGroupMember ──── Agent
```


### 2.2 数据库表结构

#### 2.2.1 用户表 (user)
```sql
CREATE TABLE user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    email VARCHAR(100) COMMENT '邮箱',
    real_name VARCHAR(50) COMMENT '真实姓名',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态:ACTIVE,DISABLED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL COMMENT '最后登录时间',
    INDEX idx_username (username),
    INDEX idx_status (status)
) COMMENT='用户表';
```

#### 2.2.2 用户权限关联表 (user_permission)
```sql
CREATE TABLE user_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    permission_code VARCHAR(50) NOT NULL COMMENT '权限代码',
    UNIQUE KEY uk_user_permission (user_id, permission_code),
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_permission_code (permission_code)
) COMMENT='用户权限关联表';
```

**权限代码列表**（在代码中定义，不存储在数据库）：
```
用户管理：user:create, user:edit, user:delete, user:view
任务管理：task:create, task:execute, task:delete, task:view
脚本管理：script:create, script:edit, script:delete, script:view
Agent管理：agent:view, agent:group
系统管理：log:view, system:settings
```

#### 2.2.3 Agent分组表 (agent_group)
```sql
CREATE TABLE agent_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE COMMENT '分组名称',
    description VARCHAR(500) COMMENT '描述',
    type VARCHAR(20) NOT NULL DEFAULT 'CUSTOM' COMMENT '类型:BUSINESS,ENVIRONMENT,REGION,CUSTOM',
    created_by VARCHAR(50) COMMENT '创建者',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name),
    INDEX idx_type (type)
) COMMENT='Agent分组表';
```

#### 2.2.4 分组成员关联表 (agent_group_member)
```sql
CREATE TABLE agent_group_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL COMMENT '分组ID',
    agent_id VARCHAR(100) NOT NULL COMMENT 'Agent ID',
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    UNIQUE KEY uk_group_agent (group_id, agent_id),
    FOREIGN KEY (group_id) REFERENCES agent_group(id) ON DELETE CASCADE,
    INDEX idx_agent_id (agent_id)
) COMMENT='分组成员关联表';
```

## 3. API设计

### 3.1 用户管理API

#### 3.1.1 获取用户列表
```
GET /api/web/users
参数：
  - page: int (页码，默认0)
  - size: int (每页大小，默认20)
  - status: String (状态筛选，可选)
  - keyword: String (搜索关键词，可选)
响应：
{
  "content": [
    {
      "id": 1,
      "username": "admin",
      "email": "admin@example.com",
      "realName": "管理员",
      "status": "ACTIVE",
      "permissions": ["user:create", "user:edit", "task:create", ...],
      "permissionCount": 16,
      "createdAt": "2026-02-28T10:00:00",
      "lastLoginAt": "2026-02-28T15:00:00"
    }
  ],
  "totalElements": 10,
  "totalPages": 1
}
```

#### 3.1.2 创建用户
```
POST /api/web/users
请求体：
{
  "username": "newuser",
  "password": "Password123",
  "email": "user@example.com",
  "realName": "新用户",
  "permissions": ["task:create", "task:view", "script:view", "agent:view"]
}
响应：
{
  "id": 5,
  "username": "newuser",
  "message": "用户创建成功"
}
```

#### 3.1.3 更新用户
```
PUT /api/web/users/{userId}
请求体：
{
  "email": "newemail@example.com",
  "realName": "更新姓名",
  "permissions": ["task:create", "task:view", "task:execute"]
}
响应：
{
  "message": "用户更新成功"
}
```

#### 3.1.4 删除用户
```
DELETE /api/web/users/{userId}
响应：
{
  "message": "用户删除成功"
}
```

#### 3.1.5 重置密码
```
POST /api/web/users/{userId}/reset-password
请求体：
{
  "newPassword": "NewPassword123"
}
响应：
{
  "message": "密码重置成功"
}
```

#### 3.1.6 禁用/启用用户
```
POST /api/web/users/{userId}/toggle-status
响应：
{
  "status": "DISABLED",
  "message": "用户已禁用"
}
```


### 3.2 权限API

#### 3.2.1 获取所有可用权限
```
GET /api/web/permissions
响应：
{
  "permissions": [
    {
      "code": "user:create",
      "name": "创建用户",
      "category": "USER",
      "description": "可以创建新用户"
    },
    {
      "code": "user:edit",
      "name": "编辑用户",
      "category": "USER",
      "description": "可以编辑用户信息"
    },
    {
      "code": "task:create",
      "name": "创建任务",
      "category": "TASK",
      "description": "可以创建新任务"
    }
    // ... 其他权限
  ],
  "categories": ["USER", "TASK", "SCRIPT", "AGENT", "SYSTEM"]
}
```

**说明**：
- 权限列表在后端代码中定义（常量或枚举）
- 前端调用此接口获取所有可用权限，用于显示权限选择界面
- 按category分组显示，方便用户理解和选择

### 3.3 Agent分组API

#### 3.3.1 获取分组列表
```
GET /api/web/agent-groups
参数：
  - type: String (类型筛选，可选)
响应：
{
  "content": [
    {
      "id": 1,
      "name": "生产环境",
      "description": "生产环境服务器",
      "type": "ENVIRONMENT",
      "agentCount": 10,
      "createdBy": "admin",
      "createdAt": "2026-02-28T10:00:00"
    }
  ]
}
```

#### 3.3.2 创建分组
```
POST /api/web/agent-groups
请求体：
{
  "name": "测试环境",
  "description": "测试环境服务器",
  "type": "ENVIRONMENT"
}
响应：
{
  "id": 2,
  "message": "分组创建成功"
}
```

#### 3.3.3 获取分组详情
```
GET /api/web/agent-groups/{groupId}
响应：
{
  "id": 1,
  "name": "生产环境",
  "description": "生产环境服务器",
  "type": "ENVIRONMENT",
  "agents": [
    {
      "agentId": "agent-001",
      "hostname": "server-01",
      "status": "ONLINE",
      "addedAt": "2026-02-28T10:00:00"
    }
  ],
  "createdBy": "admin",
  "createdAt": "2026-02-28T10:00:00"
}
```

#### 3.3.4 添加Agent到分组
```
POST /api/web/agent-groups/{groupId}/agents
请求体：
{
  "agentIds": ["agent-001", "agent-002", "agent-003"]
}
响应：
{
  "addedCount": 3,
  "message": "成功添加3个Agent到分组"
}
```

#### 3.3.5 从分组移除Agent
```
DELETE /api/web/agent-groups/{groupId}/agents
请求体：
{
  "agentIds": ["agent-001"]
}
响应：
{
  "removedCount": 1,
  "message": "成功从分组移除1个Agent"
}
```

#### 3.3.6 更新分组
```
PUT /api/web/agent-groups/{groupId}
请求体：
{
  "name": "生产环境-更新",
  "description": "更新后的描述"
}
```

#### 3.3.7 删除分组
```
DELETE /api/web/agent-groups/{groupId}
响应：
{
  "message": "分组删除成功"
}
```

#### 3.3.8 获取Agent所属分组
```
GET /api/web/agents/{agentId}/groups
响应：
{
  "groups": [
    {
      "id": 1,
      "name": "生产环境",
      "type": "ENVIRONMENT"
    }
  ]
}
```
