# 任务列表：任务多目标支持

## 实施阶段

### 阶段 1：数据模型重构（高优先级）

#### Task 1.1: 创建 TaskExecution 实体类
- [ ] 创建 `TaskExecution.java` 实体类
- [ ] 定义字段：id, taskId, agentId, executionNumber, status, logFilePath, exitCode, summary, pulledAt, startedAt, finishedAt, createdAt
- [ ] 添加 JPA 注解和索引
- [ ] 添加唯一约束：(taskId, agentId, executionNumber)
- **验收标准**: 实体类编译通过，包含所有必需字段

#### Task 1.2: 修改 Task 实体类
- [ ] 移除字段：agentId, status, executionCount, logFilePath, exitCode, pulledAt, startedAt, finishedAt
- [ ] 保留字段：taskId, taskName, scriptLang, scriptContent, timeoutSec, createdBy, createdAt
- [ ] 移除 batchId 字段（批量任务功能暂时移除）
- [ ] 添加 @Transient 字段用于聚合状态显示
- **验收标准**: 实体类编译通过，移除了执行相关字段

#### Task 1.3: 创建数据库迁移脚本
- [ ] 创建 `V4__task_multi_target_support.sql`
- [ ] 创建 task_executions 表
- [ ] 迁移现有 tasks 数据到 task_executions
- [ ] 修改 tasks 表结构（删除执行相关列）
- [ ] 创建索引和外键约束
- [ ] 创建回滚脚本（备用）
- **验收标准**: 迁移脚本在测试数据库上成功执行，数据完整迁移

#### Task 1.4: 创建 Repository 接口
- [ ] 创建 `TaskExecutionRepository.java`
- [ ] 添加查询方法：findByTaskId, findByAgentId, findByTaskIdAndAgentId
- [ ] 添加统计方法：countByTaskIdAndStatus
- [ ] 修改 `TaskRepository.java`（如需要）
- **验收标准**: Repository 接口编译通过，包含所有必需查询方法

### 阶段 2：后端服务层实现（高优先级）

#### Task 2.1: 创建 TaskExecutionService
- [ ] 创建 `TaskExecutionService.java` 接口
- [ ] 实现 `TaskExecutionServiceImpl.java`
- [ ] 实现方法：createExecution, updateStatus, getExecutionsByTaskId, getExecution
- [ ] 实现日志文件路径生成逻辑
- [ ] 添加事务管理
- **验收标准**: 服务类编译通过，所有方法有基本实现

#### Task 2.2: 重构 TaskService
- [ ] 修改 `createTask` 方法：接受 List<String> agentIds
- [ ] 实现创建 Task 和多个 TaskExecution 的逻辑
- [ ] 实现聚合状态计算方法：computeAggregatedStatus
- [ ] 实现执行进度计算方法：computeExecutionProgress
- [ ] 添加 getTaskWithAggregatedStatus 方法
- **验收标准**: TaskService 编译通过，支持多代理任务创建

#### Task 2.3: 创建 DTO 类
- [ ] 创建 `TaskDTO.java`：包含聚合状态和进度信息
- [ ] 创建 `TaskExecutionDTO.java`：执行实例信息
- [ ] 创建 `TaskSummaryDTO.java`：任务摘要信息
- [ ] 实现 Entity 到 DTO 的转换方法
- **验收标准**: DTO 类编译通过，包含所有必需字段

#### Task 2.4: 实现重启功能
- [ ] 在 TaskService 中添加 `restartTask` 方法
- [ ] 支持 RestartMode.ALL 和 RestartMode.FAILED_ONLY
- [ ] 实现 executionNumber 自动递增逻辑
- [ ] 保留历史执行记录
- **验收标准**: 重启功能正常工作，executionNumber 正确递增

#### Task 2.5: 实现取消功能
- [ ] 在 TaskExecutionService 中添加 `cancelExecution` 方法
- [ ] 在 TaskService 中添加 `cancelTask` 方法（取消所有执行）
- [ ] 实现状态验证（不能取消已完成的执行）
- [ ] 记录 finishedAt 时间戳
- **验收标准**: 取消功能正常工作，状态验证正确

### 阶段 3：API 端点实现（高优先级）

#### Task 3.1: 修改任务创建 API
- [ ] 修改 `POST /api/web/tasks/create`
- [ ] 接受 `agentIds` 参数（数组）
- [ ] 调用 TaskService.createTask 创建任务和执行实例
- [ ] 返回 taskId 和 targetAgentCount
- **验收标准**: API 测试通过，能创建多代理任务

#### Task 3.2: 创建任务执行查询 API
- [ ] 实现 `GET /api/web/tasks/{taskId}/executions`
- [ ] 返回任务的所有执行实例列表
- [ ] 支持状态过滤（可选）
- **验收标准**: API 返回正确的执行实例列表

#### Task 3.3: 创建任务摘要 API
- [ ] 实现 `GET /api/web/tasks/{taskId}/summary`
- [ ] 返回聚合状态、进度、统计信息
- **验收标准**: API 返回正确的聚合信息

#### Task 3.4: 修改任务列表 API
- [ ] 修改 `GET /api/web/tasks`
- [ ] 在响应中包含 targetAgentCount 和 executionProgress
- [ ] 计算并返回聚合状态
- **验收标准**: API 返回包含聚合信息的任务列表

#### Task 3.5: 创建重启和取消 API
- [ ] 实现 `POST /api/web/tasks/{taskId}/restart`
- [ ] 实现 `POST /api/web/tasks/executions/{executionId}/cancel`
- [ ] 实现 `POST /api/web/tasks/{taskId}/cancel`（取消所有）
- **验收标准**: API 测试通过，功能正常

#### Task 3.6: 创建执行日志 API
- [ ] 实现 `GET /api/web/tasks/executions/{executionId}/logs`
- [ ] 实现 `GET /api/web/tasks/executions/{executionId}/download`
- [ ] 支持分页和偏移量参数
- **验收标准**: API 返回正确的日志内容

### 阶段 4：前端组件重构（中优先级）

#### Task 4.1: 移除批量任务 Tab
- [ ] 从 `Tasks.jsx` 移除批量任务相关状态
- [ ] 移除批量任务 TabPane
- [ ] 移除批量任务创建模态框
- [ ] 移除批量任务详情模态框
- [ ] 清理未使用的代码和导入
- **验收标准**: 界面只显示"任务"Tab，无批量任务相关UI

#### Task 4.2: 修改任务创建表单
- [ ] 将 agentId 选择器改为多选（mode="multiple"）
- [ ] 更新表单验证：至少选择一个代理
- [ ] 更新提交逻辑：发送 agentIds 数组
- [ ] 更新 UI 提示文本
- **验收标准**: 可以选择多个代理创建任务

#### Task 4.3: 更新任务列表显示
- [ ] 添加"目标节点"列：显示代理数量
- [ ] 添加"执行进度"列：显示 X/Y 已完成
- [ ] 更新"状态"列：显示聚合状态
- [ ] 移除"Agent"列（改为在详情中显示）
- [ ] 更新表格列宽和布局
- **验收标准**: 任务列表正确显示多代理任务信息

#### Task 4.4: 创建任务详情模态框
- [ ] 创建 `TaskDetailModal` 组件
- [ ] 显示任务基本信息（名称、脚本、创建时间等）
- [ ] 显示执行实例列表（Table）
- [ ] 支持按状态过滤执行实例
- [ ] 添加查看日志、取消执行按钮
- **验收标准**: 详情模态框正确显示所有执行实例

#### Task 4.5: 创建执行日志模态框
- [ ] 创建 `ExecutionLogModal` 组件
- [ ] 显示特定执行实例的日志
- [ ] 显示 agentId、状态、时间戳
- [ ] 支持日志刷新和下载
- [ ] 处理日志为空的情况
- **验收标准**: 可以查看单个执行实例的日志

#### Task 4.6: 实现重启功能 UI
- [ ] 在任务操作中添加"重启"按钮
- [ ] 显示重启选项对话框：全部重启 / 仅重启失败
- [ ] 调用重启 API
- [ ] 刷新任务列表
- **验收标准**: 重启功能 UI 正常工作

#### Task 4.7: 更新取消功能 UI
- [ ] 支持取消整个任务（所有执行）
- [ ] 支持取消单个执行实例
- [ ] 添加确认对话框
- [ ] 更新状态显示
- **验收标准**: 取消功能 UI 正常工作

### 阶段 5：代理端适配（中优先级）

#### Task 5.1: 修改代理拉取任务 API
- [x] 修改 `GET /api/agent/tasks/pull`
- [x] 返回 TaskExecution 信息（包含 executionId）
- [x] 确保代理只能拉取分配给它的任务
- **验收标准**: 代理能正确拉取任务 ✅

#### Task 5.2: 修改代理状态更新 API
- [x] 修改状态更新 API 使用 executionId
- [x] 实现 `POST /api/agent/tasks/executions/{executionId}/ack`
- [x] 验证代理只能更新自己的执行实例
- [x] 保持向后兼容性（支持taskId和executionId）
- **验收标准**: 代理能正确更新执行状态 ✅

#### Task 5.3: 修改代理日志上传逻辑
- [x] 确保日志文件路径包含 executionId
- [x] 更新日志文件命名规则
- [x] 支持executionId参数（向后兼容taskId）
- **验收标准**: 日志正确存储和检索 ✅

### 阶段 6：测试（低优先级）

#### Task 6.1: 单元测试 - 后端
- [ ] TaskService 单元测试
- [ ] TaskExecutionService 单元测试
- [ ] 聚合状态计算测试
- [ ] 重启功能测试
- [ ] 取消功能测试
- **验收标准**: 单元测试覆盖率 > 80%

#### Task 6.2: 单元测试 - 前端
- [ ] TaskCreateForm 组件测试
- [ ] TaskList 组件测试
- [ ] TaskDetailModal 组件测试
- [ ] ExecutionLogModal 组件测试
- **验收标准**: 组件测试覆盖率 > 70%

#### Task 6.3: 集成测试
- [ ] 端到端任务创建流程测试
- [ ] 端到端任务执行流程测试
- [ ] 端到端重启流程测试
- [ ] 数据迁移测试
- **验收标准**: 所有集成测试通过

#### Task 6.4: 属性测试（可选）
- [ ] 使用 JUnit-Quickcheck 编写属性测试
- [ ] 测试 17 个正确性属性
- [ ] 每个属性测试运行 100 次迭代
- **验收标准**: 所有属性测试通过

### 阶段 7：文档和清理（低优先级）

#### Task 7.1: 更新 API 文档
- [ ] 更新 API 端点文档
- [ ] 添加请求/响应示例
- [ ] 标记已弃用的批量任务 API
- **验收标准**: API 文档完整准确

#### Task 7.2: 更新用户文档
- [ ] 编写多代理任务使用指南
- [ ] 添加截图和示例
- [ ] 说明与旧版本的差异
- **验收标准**: 用户文档清晰易懂

#### Task 7.3: 清理批量任务代码
- [ ] 标记 BatchTask 相关代码为 @Deprecated
- [ ] 添加迁移说明注释
- [ ] 保留数据库表但不在 UI 显示
- **验收标准**: 代码清理完成，保留向后兼容性

## 实施顺序建议

**第 1 周：数据模型和后端核心**
- Day 1-2: Task 1.1 - 1.4（数据模型重构）
- Day 3-4: Task 2.1 - 2.3（服务层实现）
- Day 5: Task 2.4 - 2.5（重启和取消功能）

**第 2 周：API 和前端核心**
- Day 1-2: Task 3.1 - 3.6（API 端点实现）
- Day 3: Task 4.1 - 4.2（移除批量任务，修改创建表单）
- Day 4-5: Task 4.3 - 4.5（任务列表和详情显示）

**第 3 周：前端完善和代理适配**
- Day 1: Task 4.6 - 4.7（重启和取消 UI）
- Day 2-3: Task 5.1 - 5.3（代理端适配）
- Day 4-5: 集成测试和 Bug 修复

**第 4 周：测试和文档**
- Day 1-2: Task 6.1 - 6.2（单元测试）
- Day 3: Task 6.3（集成测试）
- Day 4-5: Task 7.1 - 7.3（文档和清理）

## 当前状态

- [x] 阶段 1：数据模型重构 ✅
- [x] 阶段 2：后端服务层实现 ✅
- [x] 阶段 3：API 端点实现 ✅
- [x] 阶段 4：前端组件重构 ✅
- [x] 阶段 5：代理端适配 ✅
- [ ] 阶段 6：测试 🔄 下一步
- [ ] 阶段 7：文档和清理

## 风险和依赖

**关键依赖：**
- 数据迁移脚本必须在所有其他工作之前完成并测试
- 后端 API 必须在前端重构之前完成
- 代理端适配必须与后端 API 同步

**风险缓解：**
- 在测试环境完整测试数据迁移
- 保持 API 向后兼容性
- 提供回滚方案
