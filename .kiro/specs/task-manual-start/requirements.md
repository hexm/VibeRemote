# 任务手动启动功能需求文档

## 1. 功能概述
允许用户在创建任务时选择是否立即启动。如果选择不立即启动，任务将保持草稿状态，需要手动启动后才会创建执行实例并分配给Agent执行。

## 2. 用户故事

### 2.1 创建任务时选择启动方式
**作为** 系统管理员  
**我想要** 在创建任务时选择是否立即启动  
**以便** 我可以先配置好任务，稍后再启动执行

**验收标准**：
- 创建任务API支持 `autoStart` 参数（布尔值）
- `autoStart=true`：立即创建执行实例（当前行为）
- `autoStart=false`：任务保持DRAFT状态，不创建执行实例
- 前端界面提供"立即启动"选项（默认勾选）

### 2.2 查看草稿任务
**作为** 系统管理员  
**我想要** 查看所有草稿状态的任务  
**以便** 我知道哪些任务还未启动

**验收标准**：
- 任务列表显示任务状态（DRAFT/ACTIVE）
- 可以按状态筛选任务
- 草稿任务显示特殊标识

### 2.3 手动启动任务
**作为** 系统管理员  
**我想要** 手动启动草稿状态的任务  
**以便** 任务开始执行

**验收标准**：
- 提供"启动任务"API接口
- 启动后任务状态变为ACTIVE
- 为所有目标代理创建执行实例
- 执行实例状态为PENDING，可被Agent拉取

### 2.4 重启任务
**作为** 系统管理员  
**我想要** 重启已完成或失败的任务  
**以便** 任务重新执行

**验收标准**：
- 保持现有重启功能
- 重启时创建新的执行实例
- 执行次数递增

### 2.5 停止任务
**作为** 系统管理员  
**我想要** 停止正在执行的任务  
**以便** 取消不需要的任务

**验收标准**：
- 提供"停止任务"API接口
- 停止后任务状态变为STOPPED
- 取消所有未完成的执行实例

## 3. 功能需求

### 3.1 任务状态定义（统一状态）
- **DRAFT**: 草稿，任务已创建但未启动
- **PENDING**: 待执行，任务已启动，等待Agent拉取
- **RUNNING**: 执行中，至少有一个执行实例在运行
- **SUCCESS**: 成功，所有执行实例都成功完成
- **FAILED**: 失败，所有执行实例都失败
- **PARTIAL_SUCCESS**: 部分成功，部分执行实例成功，部分失败
- **STOPPED**: 已停止，任务被手动停止
- **CANCELLED**: 已取消，任务被取消

状态说明：
- 任务状态从执行实例状态自动计算得出
- DRAFT状态是唯一的手动设置状态（创建时）
- 其他状态都是根据执行实例状态自动更新

### 3.2 API接口

#### 3.2.1 创建任务（修改）
```
POST /api/web/tasks/create
参数：
  - agentIds: List<String> (必需)
  - taskName: String (必需)
  - autoStart: Boolean (可选，默认true)
  - taskSpec: TaskSpec (必需)

响应：
  - taskId: String
  - taskStatus: String (DRAFT/PENDING)
  - targetAgentCount: Integer
  - message: String
```

#### 3.2.2 启动任务（新增）
```
POST /api/web/tasks/{taskId}/start

响应：
  - taskId: String
  - taskStatus: String (PENDING)
  - executionCount: Integer
  - message: String
```

#### 3.2.3 停止任务（新增）
```
POST /api/web/tasks/{taskId}/stop

响应：
  - taskId: String
  - taskStatus: String (STOPPED)
  - cancelledCount: Integer
  - message: String
```

### 3.3 数据库变更
在Task表增加字段：
- `task_status`: VARCHAR(20) - 任务状态（DRAFT/ACTIVE/STOPPED/COMPLETED）
- 默认值：根据autoStart参数决定

### 3.4 业务规则

#### 3.4.1 创建任务
- 如果autoStart=true：任务状态为PENDING，立即创建执行实例
- 如果autoStart=false：任务状态为DRAFT，不创建执行实例

#### 3.4.2 启动任务
- 只能启动DRAFT状态的任务
- 启动后为所有目标代理创建执行实例
- 任务状态自动变为PENDING

#### 3.4.3 停止任务
- 可以停止PENDING/RUNNING状态的任务
- 取消所有未完成的执行实例
- 任务状态变为STOPPED

#### 3.4.4 重启任务
- 可以重启SUCCESS/FAILED/PARTIAL_SUCCESS/STOPPED状态的任务
- 创建新的执行实例
- 任务状态变为PENDING

#### 3.4.5 状态自动更新规则
任务状态根据执行实例状态自动计算：
- 所有执行实例都是PENDING → 任务状态=PENDING
- 至少一个RUNNING → 任务状态=RUNNING
- 所有执行实例都完成：
  - 全部SUCCESS → 任务状态=SUCCESS
  - 全部FAILED → 任务状态=FAILED
  - 部分SUCCESS部分FAILED → 任务状态=PARTIAL_SUCCESS
  - 全部CANCELLED → 任务状态=CANCELLED

#### 3.4.6 Agent拉取任务
- 只能拉取非DRAFT、非STOPPED、非CANCELLED状态任务的PENDING执行实例

## 4. 非功能需求

### 4.1 性能要求
- 启动任务操作应在1秒内完成
- 停止任务操作应在2秒内完成

### 4.2 兼容性要求
- 保持现有API的向后兼容性
- autoStart参数默认为true，保持现有行为

### 4.3 安全要求
- 只有任务创建者或管理员可以启动/停止任务

## 5. 约束条件
- 草稿任务不能被Agent拉取
- 停止的任务可以重新启动
- 任务状态变更需要记录日志

## 6. 验收测试场景

### 场景1：创建草稿任务
1. 创建任务，autoStart=false
2. 验证任务状态为DRAFT
3. 验证没有创建执行实例
4. 验证Agent无法拉取该任务

### 场景2：启动草稿任务
1. 创建草稿任务
2. 调用启动API
3. 验证任务状态变为ACTIVE
4. 验证创建了执行实例
5. 验证Agent可以拉取任务

### 场景3：停止活动任务
1. 创建并启动任务
2. 调用停止API
3. 验证任务状态变为STOPPED
4. 验证执行实例被取消

### 场景4：重启任务
1. 创建并完成任务
2. 调用重启API
3. 验证创建了新的执行实例
4. 验证执行次数递增

## 7. 优先级
- P0（高优先级）：创建任务时选择是否启动
- P0（高优先级）：手动启动草稿任务
- P1（中优先级）：停止活动任务
- P1（中优先级）：前端界面支持

## 8. 依赖关系
- 依赖现有的多目标任务功能
- 依赖现有的任务重启功能
