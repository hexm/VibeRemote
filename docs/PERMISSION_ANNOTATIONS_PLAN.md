# 权限注解添加计划

## WebController权限注解

### Agent相关
- `GET /api/web/dashboard/stats` → `@RequirePermission("agent:view")` (查看统计需要agent:view)
- `GET /api/web/agents` → `@RequirePermission("agent:view")`
- `GET /api/web/agents/{agentId}` → `@RequirePermission("agent:view")`
- `GET /api/web/agents/{agentId}/tasks` → `@RequirePermission("agent:view")`
- `GET /api/web/agents/{agentId}/groups` → `@RequirePermission("agent:view")`

### Task相关
- `GET /api/web/tasks` → `@RequirePermission("task:view")`
- `GET /api/web/tasks/{taskId}` → `@RequirePermission("task:view")`
- `GET /api/web/tasks/{taskId}/summary` → `@RequirePermission("task:view")`
- `POST /api/web/tasks/create` → `@RequirePermission("task:create")`
- `POST /api/web/tasks/{taskId}/start` → `@RequirePermission("task:execute")`
- `POST /api/web/tasks/{taskId}/stop` → `@RequirePermission("task:execute")`
- `GET /api/web/tasks/{taskId}/executions` → `@RequirePermission("task:view")`
- `POST /api/web/tasks/{taskId}/restart` → `@RequirePermission("task:execute")`
- `POST /api/web/tasks/{taskId}/cancel` → `@RequirePermission("task:execute")`
- `POST /api/web/tasks/executions/{executionId}/cancel` → `@RequirePermission("task:execute")`
- `GET /api/web/tasks/executions/{executionId}/logs` → `@RequirePermission("log:view")`
- `GET /api/web/tasks/executions/{executionId}/download` → `@RequirePermission("log:view")`

### Script相关 (如果有ScriptController)
- `GET /api/web/scripts` → `@RequirePermission("script:view")`
- `GET /api/web/scripts/{scriptId}` → `@RequirePermission("script:view")`
- `POST /api/web/scripts` → `@RequirePermission("script:create")`
- `PUT /api/web/scripts/{scriptId}` → `@RequirePermission("script:edit")`
- `DELETE /api/web/scripts/{scriptId}` → `@RequirePermission("script:delete")`

### AgentGroup相关 (AgentGroupController)
- `GET /api/web/agent-groups` → `@RequirePermission("agent:group")`
- `GET /api/web/agent-groups/{groupId}` → `@RequirePermission("agent:group")`
- `POST /api/web/agent-groups` → `@RequirePermission("agent:group")`
- `PUT /api/web/agent-groups/{groupId}` → `@RequirePermission("agent:group")`
- `DELETE /api/web/agent-groups/{groupId}` → `@RequirePermission("agent:group")`
- `POST /api/web/agent-groups/{groupId}/members` → `@RequirePermission("agent:group")`
- `DELETE /api/web/agent-groups/{groupId}/members/{agentId}` → `@RequirePermission("agent:group")`

## 实施步骤

1. ✅ UserController - 已完成
2. ✅ WebController - 已完成
3. ✅ AgentGroupController - 已完成
4. ⏳ PermissionController - 待完成(可能不需要权限,因为是查询权限列表)

## 注意事项

1. 登录相关API不需要权限注解(已在SecurityConfig中配置为permitAll)
2. Agent注册API不需要权限注解(Agent自动注册)
3. 健康检查API不需要权限注解
4. 权限查询API可能不需要权限(所有登录用户都可以查看权限列表)
