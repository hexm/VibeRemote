# 任务手动启动功能实施总结

## 实施日期
2026-02-28

## 功能概述
实现了任务手动启动功能，允许用户在创建任务时选择是否立即启动。如果选择不立即启动，任务将保持草稿状态，需要手动启动后才会创建执行实例并分配给Agent执行。

## 已完成的工作

### 1. 数据库变更
- ✅ 创建数据库迁移脚本 `V5__task_manual_start_support.sql`
- ✅ 添加 `task_status` 字段到 Task 表（VARCHAR(20)，默认'PENDING'）
- ✅ 添加 `task_status` 索引以提升查询性能
- ✅ 更新现有数据的 `task_status`（根据执行实例状态计算）

### 2. 实体层修改
- ✅ Task 实体增加 `taskStatus` 字段
- ✅ 添加字段注释说明状态含义

### 3. Repository 层修改
- ✅ TaskRepository 增加 `findByTaskStatus` 方法
- ✅ TaskRepository 增加 `countByTaskStatus` 方法
- ✅ TaskExecutionRepository 增加 `findPendingExecutionsForActiveTasks` 方法
  - 使用 @Query 注解，JOIN Task 表
  - 排除 DRAFT/STOPPED/CANCELLED 状态的任务

### 4. DTO 和 Model
- ✅ 创建 `StartTaskResponse` 类
  - taskId: String
  - taskStatus: String
  - executionCount: Integer
  - message: String
- ✅ 创建 `StopTaskResponse` 类
  - taskId: String
  - taskStatus: String
  - cancelledCount: Integer
  - message: String
- ✅ 修改 `CreateTaskResponse` 包含 `taskStatus` 字段
- ✅ 修改 `TaskDTO` 包含 `taskStatus` 字段

### 5. Service 层实现 - 核心状态管理
- ✅ TaskService 增加 `calculateTaskStatus` 方法
  - 根据执行实例状态计算任务状态
  - 实现完整的状态计算逻辑
- ✅ TaskService 增加 `updateTaskStatus` 方法
  - 调用 calculateTaskStatus 计算新状态
  - 更新 Task 实体并保存
  - 记录状态变更日志

### 6. Service 层实现 - 任务启动功能
- ✅ TaskService 增加 `startTask(taskId, agentIds)` 方法
  - 验证任务状态为 DRAFT
  - 为所有目标代理创建执行实例
  - 调用 updateTaskStatus 更新状态
  - 返回 StartTaskResponse

### 7. Service 层实现 - 任务停止功能
- ✅ TaskService 增加 `stopTask` 方法
  - 验证任务状态可停止（PENDING/RUNNING）
  - 取消所有未完成的执行实例
  - 调用 updateTaskStatus 更新状态
  - 返回 StopTaskResponse

### 8. Service 层实现 - 修改现有方法
- ✅ 修改 `createMultiAgentTask` 方法
  - 增加 `autoStart` 参数（默认 true）
  - 根据 autoStart 设置初始状态
  - 如果 autoStart=false，不创建执行实例
- ✅ 修改 `pullTasks` 方法
  - 使用新的 Repository 方法
  - 只查询可执行状态任务的执行实例
- ✅ 修改 `restartTask` 方法
  - 验证任务状态可重启
  - 重启后调用 updateTaskStatus
- ✅ 修改 `ackTaskExecution` 方法
  - 执行后调用 updateTaskStatus
- ✅ 修改 `finishTask` 方法
  - 完成后调用 updateTaskStatus
- ✅ 修改 `cancelExecution` 方法
  - 取消后调用 updateTaskStatus

### 9. Controller 层实现
- ✅ WebController 增加 `startTask` 接口
  - 路径：POST /api/web/tasks/{taskId}/start
  - 参数：taskId, agentIds
  - 返回：StartTaskResponse
- ✅ WebController 增加 `stopTask` 接口
  - 路径：POST /api/web/tasks/{taskId}/stop
  - 参数：taskId
  - 返回：StopTaskResponse
- ✅ 修改 `createTask` 接口
  - 增加 autoStart 参数（默认 true）
  - 响应中包含 taskStatus 字段

### 10. 测试
- ✅ 创建测试脚本 `test-manual-start.sh`
- ✅ 手动测试所有功能
  - 创建草稿任务（autoStart=false）✓
  - 验证草稿任务没有执行实例 ✓
  - 启动草稿任务 ✓
  - 验证启动后状态变为 PENDING ✓
  - 验证任务执行完成后状态自动变为 SUCCESS ✓
  - 创建立即启动的任务（autoStart=true）✓
  - 停止正在运行的任务 ✓
  - 验证停止后状态变为 CANCELLED ✓
  - 查询任务列表包含 taskStatus 字段 ✓

## 任务状态定义

| 状态 | 说明 | 如何产生 |
|------|------|----------|
| DRAFT | 草稿 | 创建时 autoStart=false |
| PENDING | 待执行 | 启动任务/重启任务，所有执行实例都是 PENDING |
| RUNNING | 执行中 | 至少一个执行实例在运行 |
| SUCCESS | 成功 | 所有执行实例都成功完成 |
| FAILED | 失败 | 所有执行实例都失败 |
| PARTIAL_SUCCESS | 部分成功 | 部分执行实例成功，部分失败 |
| STOPPED | 已停止 | 手动停止任务（已废弃，使用 CANCELLED） |
| CANCELLED | 已取消 | 所有执行实例都被取消 |

## API 接口

### 1. 创建任务（修改）
```
POST /api/web/tasks/create
参数：
  - agentIds: List<String> (必需)
  - taskName: String (必需)
  - autoStart: Boolean (可选，默认 true)
  - taskSpec: TaskSpec (必需，Body)

响应：
{
  "taskId": "xxx",
  "taskStatus": "DRAFT" | "PENDING",
  "targetAgentCount": 1,
  "message": "任务创建成功"
}
```

### 2. 启动任务（新增）
```
POST /api/web/tasks/{taskId}/start
参数：
  - taskId: String (路径参数)
  - agentIds: List<String> (查询参数)

响应：
{
  "taskId": "xxx",
  "taskStatus": "PENDING",
  "executionCount": 1,
  "message": "任务已启动，创建了 1 个执行实例"
}
```

### 3. 停止任务（新增）
```
POST /api/web/tasks/{taskId}/stop
参数：
  - taskId: String (路径参数)

响应：
{
  "taskId": "xxx",
  "taskStatus": "CANCELLED",
  "cancelledCount": 1,
  "message": "任务已停止，取消了 1 个执行实例"
}
```

## 状态自动更新机制

任务状态会在以下时机自动更新：
1. 创建执行实例后
2. 执行实例状态变更后（ACK、完成、取消）
3. 启动任务后
4. 停止任务后
5. 重启任务后

状态计算规则：
- 没有执行实例 → DRAFT
- 有 RUNNING 或 PULLED → RUNNING
- 全部 SUCCESS → SUCCESS
- 全部 FAILED → FAILED
- 全部 CANCELLED → CANCELLED
- 部分 SUCCESS 部分 FAILED → PARTIAL_SUCCESS
- 全部 PENDING → PENDING

## Agent 拉取任务逻辑

Agent 只能拉取以下状态任务的 PENDING 执行实例：
- 非 DRAFT 状态
- 非 STOPPED 状态
- 非 CANCELLED 状态

这确保了草稿任务和已停止的任务不会被 Agent 拉取执行。

## 测试结果

所有核心功能测试通过：
- ✅ 创建草稿任务
- ✅ 启动草稿任务
- ✅ 任务状态自动更新
- ✅ 停止运行中的任务
- ✅ 任务列表包含状态字段
- ✅ Agent 只拉取可执行状态的任务

## 待完成的工作

### 前端实现
- [ ] 创建任务界面增加"立即启动"复选框
- [ ] 任务列表显示任务状态标签
- [ ] 任务列表增加状态筛选器
- [ ] 任务详情增加启动/停止按钮
- [ ] 实现 startTask 和 stopTask API 调用

### 测试
- [ ] 单元测试
- [ ] 集成测试
- [ ] 端到端测试

### 文档
- [ ] 更新 API 文档
- [ ] 更新用户手册

### 部署
- [ ] 部署到测试环境
- [ ] 部署到生产环境（阿里云）

## 技术细节

### 数据库迁移
使用 Flyway 自动执行数据库迁移脚本。迁移脚本会：
1. 添加 task_status 字段
2. 添加索引
3. 根据现有执行实例状态计算并更新 task_status

### 向后兼容性
- autoStart 参数默认为 true，保持现有行为
- 现有 API 调用不受影响
- 旧的单代理方法仍然可用（已标记为 @Deprecated）

### 性能优化
- task_status 字段添加了索引
- Agent 拉取任务时增加了 task_status 条件，减少查询范围
- 状态计算使用 Stream API，性能良好

## 注意事项

1. **启动任务需要提供代理列表**：由于当前设计中没有保存任务的目标代理列表，启动草稿任务时需要用户重新提供代理列表。

2. **状态自动更新**：任务状态会根据执行实例状态自动更新，无需手动设置。

3. **STOPPED vs CANCELLED**：设计文档中定义了 STOPPED 状态，但实际实现中，停止任务后状态会变为 CANCELLED（因为所有执行实例都被取消）。

## 下一步计划

1. 实现前端界面
2. 编写自动化测试
3. 部署到阿里云测试环境
4. 用户验收测试
5. 部署到生产环境

## 相关文档

- 需求文档：`.kiro/specs/task-manual-start/requirements.md`
- 设计文档：`.kiro/specs/task-manual-start/design.md`
- 任务列表：`.kiro/specs/task-manual-start/tasks.md`
- 测试脚本：`scripts/mac/test-manual-start.sh`
