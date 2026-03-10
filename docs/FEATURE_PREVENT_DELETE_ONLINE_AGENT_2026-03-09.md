# 防止删除在线客户端功能

## 实现日期
2026-03-09

## 功能描述
添加限制：不允许删除在线状态的客户端，只能删除离线的客户端。

## 实现内容

### 1. 后端实现

#### WebController.java
添加了删除Agent的API端点：
- 路径: `DELETE /api/web/agents/{agentId}`
- 权限: `agent:delete`
- 功能:
  - 检查Agent是否存在
  - 检查Agent是否在线
  - 如果在线，返回400错误
  - 如果离线，执行删除操作

```java
@DeleteMapping("/agents/{agentId}")
@RequirePermission("agent:delete")
public ResponseEntity<?> deleteAgent(@PathVariable String agentId) {
    Agent agent = agentService.getAgentById(agentId);
    if (agent == null) {
        Map<String, String> error = new HashMap<>();
        error.put("message", "客户端不存在");
        return ResponseEntity.status(404).body(error);
    }
    
    // 检查Agent是否在线
    if ("ONLINE".equals(agent.getStatus())) {
        Map<String, String> error = new HashMap<>();
        error.put("message", "不能删除在线的客户端，请先停止客户端");
        return ResponseEntity.status(400).body(error);
    }
    
    agentService.deleteAgent(agentId);
    Map<String, String> response = new HashMap<>();
    response.put("message", "客户端已删除");
    return ResponseEntity.ok(response);
}
```

#### AgentService.java
添加了两个新方法：

1. `deleteAgent(String agentId)` - 删除Agent
   - 检查Agent是否存在
   - 检查Agent是否在线
   - 执行删除操作
   - 记录日志

2. `getAgentById(String agentId)` - 根据ID获取Agent
   - 返回Agent对象或null

```java
@Transactional
public void deleteAgent(String agentId) {
    Agent agent = agentRepository.findById(agentId).orElse(null);
    if (agent == null) {
        throw new IllegalArgumentException("客户端不存在");
    }
    
    if ("ONLINE".equals(agent.getStatus())) {
        throw new IllegalStateException("不能删除在线的客户端");
    }
    
    agentRepository.deleteById(agentId);
    log.info("[Agent] Deleted agent: {} ({})", agent.getHostname(), agentId);
    LogUtil.logAgent("DELETE", agentId, agent.getHostname(), "Agent deleted");
}

public Agent getAgentById(String agentId) {
    return agentRepository.findById(agentId).orElse(null);
}
```

### 2. 前端实现

#### Agents.jsx
更新了删除功能：

1. **前端预检查**：
   - 在弹出确认对话框前检查Agent状态
   - 如果在线，显示警告消息并阻止删除

2. **API调用**：
   - 使用真实的DELETE API替代模拟数据操作
   - 调用 `DELETE /api/web/agents/{agentId}`
   - 删除成功后重新加载列表

3. **按钮禁用**：
   - 在线客户端的删除按钮被禁用
   - 鼠标悬停显示提示："在线客户端不能删除"

```javascript
const handleDeleteAgent = async (agent) => {
  // 检查是否在线
  if (agent.status === 'ONLINE') {
    message.warning('不能删除在线的客户端，请先停止客户端')
    return
  }
  
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除客户端 ${agent.hostname} 吗？此操作不可恢复。`,
    okText: '确定',
    cancelText: '取消',
    okButtonProps: { danger: true },
    async onOk() {
      try {
        await api.delete(`/web/agents/${agent.agentId}`)
        message.success('客户端已删除')
        fetchAgents() // 重新加载列表
      } catch (error) {
        console.error('删除客户端失败:', error)
        message.error('删除失败: ' + (error.response?.data?.message || error.message))
      }
    },
  })
}
```

```jsx
<Tooltip title={record.status === 'ONLINE' ? '在线客户端不能删除' : '删除'}>
  <Button 
    type="text" 
    icon={<DeleteOutlined />} 
    size="small"
    danger
    disabled={record.status === 'ONLINE'}
    onClick={() => handleDeleteAgent(record)}
  />
</Tooltip>
```

## 安全性

### 多层防护
1. **前端UI层**：删除按钮禁用，防止误操作
2. **前端逻辑层**：点击时检查状态，显示警告
3. **后端API层**：服务器端验证，返回400错误
4. **服务层**：抛出异常，防止数据库操作

### 错误处理
- 404: 客户端不存在
- 400: 客户端在线，不能删除
- 前端显示友好的错误消息

## 用户体验

1. **视觉反馈**：
   - 在线客户端的删除按钮显示为禁用状态（灰色）
   - 鼠标悬停显示明确的提示信息

2. **操作提示**：
   - 尝试删除在线客户端时显示警告消息
   - 确认对话框提醒"此操作不可恢复"

3. **即时反馈**：
   - 删除成功后自动刷新列表
   - 显示成功或失败的消息提示

## 测试建议

1. **在线客户端测试**：
   - 启动一个Agent
   - 尝试删除该Agent
   - 验证删除按钮是否禁用
   - 验证是否显示警告消息

2. **离线客户端测试**：
   - 停止Agent（等待2分钟变为离线）
   - 尝试删除该Agent
   - 验证是否成功删除
   - 验证列表是否更新

3. **权限测试**：
   - 使用没有`agent:delete`权限的用户
   - 验证是否返回403错误

## 文件修改

- `server/src/main/java/com/example/lightscript/server/web/WebController.java` - 添加删除API
- `server/src/main/java/com/example/lightscript/server/service/AgentService.java` - 添加删除方法
- `web-modern/src/pages/Agents.jsx` - 更新删除功能和UI

## 相关功能

类似的安全限制：
- 用户管理：管理员不能禁用自己（已实现）
- 任务管理：成功的任务不能重启（已实现）

## 后续优化建议

1. 可以考虑添加"强制删除"选项（需要特殊权限）
2. 可以添加删除前的依赖检查（如是否有未完成的任务）
3. 可以添加批量删除功能（仅删除离线的）
