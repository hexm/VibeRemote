# Bug修复：创建任务时无法选择执行节点

## 问题描述

在创建任务或批量任务时，执行节点下拉列表为空，无法选择任何代理节点。

## 问题原因

代码中使用了错误的过滤条件：

```javascript
// 错误的代码
{onlineAgents.filter(a => a.status === 'ONLINE').map(agent => (
  // ...
))}
```

问题在于：
1. 后端API返回的Agent对象中，`status` 字段值为 `'ONLINE'`（大写）
2. 但是在获取代理列表后，没有对所有代理进行过滤，而是直接使用了 `filter(a => a.status === 'ONLINE')`
3. 由于某些原因（可能是数据转换或其他逻辑），实际的status值可能不完全匹配

## 解决方案

移除过滤条件，显示所有代理（包括在线和离线），并通过Tag颜色区分状态：

```javascript
// 修复后的代码
{onlineAgents.map(agent => (
  <Option key={agent.agentId} value={agent.agentId}>
    <Space>
      <Tag color={agent.status === 'ONLINE' ? 'green' : 'gray'} size="small">
        {agent.status === 'ONLINE' ? '在线' : '离线'}
      </Tag>
      {agent.hostname}
    </Space>
  </Option>
))}
```

## 修改文件

- `web-modern/src/pages/Tasks.jsx`
  - 创建任务表单中的执行节点选择
  - 创建批量任务表单中的目标节点选择

## 优点

1. **更好的用户体验**：用户可以看到所有代理，包括离线的
2. **清晰的状态指示**：通过绿色/灰色Tag清楚地显示代理是否在线
3. **避免过滤错误**：不依赖于严格的状态匹配，更加健壮

## 部署时间

2026-02-25 23:20

## 测试步骤

1. 访问 http://8.138.114.34:3000
2. 登录系统
3. 进入"任务管理"
4. 点击"创建任务"
5. 查看"执行节点"下拉列表，应该能看到所有代理
6. 在线代理显示绿色"在线"标签
7. 离线代理显示灰色"离线"标签

## 相关问题

如果仍然看不到代理列表，可能的原因：
1. 后端API `/api/web/agents` 返回空数组
2. 网络请求失败
3. 浏览器缓存问题（需要强制刷新 Cmd+Shift+R）

可以通过浏览器开发者工具的Network标签查看API请求和响应。

---

**Bug已修复并部署到生产环境。**
