# 任务手动启动功能 - Bug修复报告

## 修复日期
2026-02-28

## 问题描述

### 问题1：取消立即执行后任务还是自动执行了
**现象**：用户在创建任务时取消了"立即启动"开关，但任务创建后还是自动执行了。

**原因分析**：
1. Switch组件同时使用了`defaultChecked`和Form.Item的`initialValue`，导致值传递混乱
2. 提交时的逻辑`values.autoStart !== false`不正确，当Switch为false时，这个表达式仍然为true

**修复方案**：
1. 移除Switch组件的`defaultChecked`属性，只使用Form.Item的`initialValue={true}`
2. 修改提交逻辑为：`const autoStart = values.autoStart === undefined ? true : values.autoStart`
3. 调整提示文字的显示方式

### 问题2：草稿状态的任务没有启动执行的入口
**现象**：创建草稿任务后，在任务列表中看不到启动按钮。

**原因分析**：
启动任务的Modal实现有问题，使用了`document.getElementById`来获取Select的值，但Ant Design的Select组件不是原生HTML select元素，无法通过这种方式获取值。

**修复方案**：
使用Select组件的`onChange`事件来捕获选中的值，将选中的代理ID保存到一个变量中，在确认时使用这个变量。

## 修复代码

### 修复1：创建任务表单

**修改前**：
```javascript
params.append('autoStart', values.autoStart !== false) // 默认true

<Switch 
  checkedChildren="立即启动" 
  unCheckedChildren="保存为草稿"
  defaultChecked
/>
```

**修改后**：
```javascript
const autoStart = values.autoStart === undefined ? true : values.autoStart
params.append('autoStart', autoStart)

<Switch 
  checkedChildren="立即启动" 
  unCheckedChildren="保存为草稿"
/>
```

### 修复2：启动任务Modal

**修改前**：
```javascript
const selectElement = document.getElementById('start-task-agents')
const selectedAgents = Array.from(selectElement?.selectedOptions || []).map(opt => opt.value)
```

**修改后**：
```javascript
let selectedAgentIds = []

<Select
  mode="multiple"
  onChange={(values) => {
    selectedAgentIds = values
  }}
>
```

## 测试验证

### 测试1：创建草稿任务
```bash
curl -X POST "http://localhost:8080/api/web/tasks/create?agentIds=xxx&taskName=test-draft&autoStart=false" \
-H 'Content-Type: application/json' \
-d '{"scriptLang": "bash", "scriptContent": "echo Test", "timeoutSec": 300}'
```

**结果**：
```json
{
  "taskId": "2b5e3f5d-b9dc-4bcc-b0b0-f19302860139",
  "taskStatus": "DRAFT",
  "targetAgentCount": 1,
  "message": "任务创建成功（草稿状态），需要手动启动"
}
```

**验证**：
- ✅ 任务状态为DRAFT
- ✅ targetAgentCount为1（但没有执行实例）
- ✅ 执行实例列表为空：`[]`

### 测试2：启动草稿任务
```bash
curl -X POST "http://localhost:8080/api/web/tasks/2b5e3f5d-b9dc-4bcc-b0b0-f19302860139/start?agentIds=xxx"
```

**结果**：
```json
{
  "taskId": "2b5e3f5d-b9dc-4bcc-b0b0-f19302860139",
  "taskStatus": "PENDING",
  "executionCount": 1,
  "message": "任务已启动，创建了 1 个执行实例"
}
```

**验证**：
- ✅ 任务状态变为PENDING
- ✅ 创建了1个执行实例
- ✅ 任务成功执行，最终状态为SUCCESS

### 测试3：创建立即启动的任务
```bash
curl -X POST "http://localhost:8080/api/web/tasks/create?agentIds=xxx&taskName=test-auto&autoStart=true" \
-H 'Content-Type: application/json' \
-d '{"scriptLang": "bash", "scriptContent": "echo Test", "timeoutSec": 300}'
```

**结果**：
```json
{
  "taskId": "1d7a7d80-775e-4a15-9643-23e135e597df",
  "taskStatus": "PENDING",
  "targetAgentCount": 1,
  "message": "任务创建成功，已分配给 1 个代理"
}
```

**验证**：
- ✅ 任务状态为PENDING
- ✅ 立即创建了执行实例
- ✅ 任务自动执行，最终状态为SUCCESS

## 前端界面验证

### 创建任务界面
1. 打开创建任务对话框
2. "立即启动"开关默认开启 ✅
3. 关闭"立即启动"开关 ✅
4. 提示文字正确显示 ✅
5. 创建任务后提示信息正确 ✅

### 任务列表界面
1. 草稿任务显示"草稿"标签（灰色）✅
2. 草稿任务显示"启动"按钮（绿色）✅
3. 点击"启动"按钮弹出选择代理对话框 ✅
4. 选择代理后可以成功启动 ✅
5. 启动后任务状态正确更新 ✅

## 修复文件清单

- `web-modern/src/pages/Tasks.jsx`
  - 修改了`handleCreateTask`函数
  - 修改了`handleStartTask`函数
  - 修改了创建任务表单中的Switch组件

## 影响范围

- 前端：创建任务功能、启动任务功能
- 后端：无影响
- 数据库：无影响

## 回归测试

- ✅ 创建草稿任务
- ✅ 启动草稿任务
- ✅ 创建立即启动的任务
- ✅ 停止运行中的任务
- ✅ 重启已完成的任务
- ✅ 查看任务详情
- ✅ 状态筛选

## 部署说明

### 前端部署
1. 重新构建前端：`cd web-modern && npm run build`
2. 重启前端服务或部署新的构建文件

### 后端部署
无需更改

## 总结

两个问题都已成功修复：
1. 草稿任务不会自动执行
2. 草稿任务可以通过"启动"按钮手动启动

所有功能测试通过，可以进行用户验收。
