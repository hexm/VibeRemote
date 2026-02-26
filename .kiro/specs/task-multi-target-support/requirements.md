# 需求文档：任务多目标支持

## 简介

本功能增强任务管理系统，使其原生支持多代理执行。当前系统存在设计问题：任务（Task）只能针对单个代理，而批量任务（BatchTask）与普通任务的创建界面几乎相同，导致概念混淆。

**当前问题：**
- 概念混淆：任务和批量任务都是"脚本分发"，不应该是两个独立的概念
- 功能重复：两个创建界面几乎完全相同
- 架构错误：批量任务应该是"任务编排"而非"多代理执行"

**正确的架构：**
- **任务（Task）** = 1个脚本 + N个目标代理（N≥1）
- **批量任务（Workflow）** = 任务1 → 任务2 → 任务3（带依赖关系）- 第二阶段实现

本规范专注于第一阶段：增强任务功能以原生支持多个目标代理，包括执行实例跟踪和管理。

## 术语表

- **任务（Task）**：由脚本和一个或多个目标代理组成的工作单元
- **任务执行（TaskExecution）**：代表一个代理执行任务的单个执行实例
- **代理（Agent）**：可以拉取和执行任务的远程系统
- **脚本（Script）**：要由代理执行的代码内容（shell、Python等）
- **执行实例（Execution_Instance）**：与TaskExecution相同 - 一个代理的执行尝试
- **多目标任务（Multi_Target_Task）**：配置为在多个代理上同时执行的任务
- **执行进度（Execution_Progress）**：已完成执行数与总目标代理数的比率
- **聚合状态（Aggregated_Status）**：从所有执行实例状态派生的整体任务状态
- **部分重启（Partial_Restart）**：仅重启失败的执行实例而不影响成功的实例
- **Web界面（Web_UI）**：用于任务管理的前端Web界面
- **API接口（API）**：后端REST API端点

## 需求

### 需求 1：多代理任务创建

**用户故事：** 作为用户，我希望创建一个可以在多个代理上同时执行的任务，以便高效分发工作而无需创建重复任务。

#### 验收标准

1. WHEN 用户创建任务时，THE Web界面 SHALL 允许从可用代理列表中选择一个或多个代理
2. WHEN 选择多个代理时，THE API接口 SHALL 创建一条任务记录和N条任务执行记录（N等于所选代理数量）
3. WHEN 创建多代理任务时，THE 系统 SHALL 为每个任务执行分配唯一标识符并关联到父任务
4. WHEN 未选择任何代理时，THE Web界面 SHALL 阻止任务创建并显示验证错误消息
5. THE 任务 SHALL 仅存储一次脚本内容、语言、超时时间和任务名称，不受目标代理数量影响

### 需求 2：执行实例跟踪

**用户故事：** 作为用户，我希望独立跟踪每个代理的执行情况，以便监控单个代理的进度并排查故障。

#### 验收标准

1. WHEN 创建包含N个代理的任务时，THE 系统 SHALL 创建N条状态为PENDING的任务执行记录
2. WHEN 代理拉取任务时，THE 系统 SHALL 将相应任务执行的状态更新为PULLED
3. WHEN 代理开始执行时，THE 系统 SHALL 将任务执行状态更新为RUNNING并记录startedAt时间戳
4. WHEN 代理完成执行时，THE 系统 SHALL 将任务执行状态更新为SUCCESS或FAILED并记录exitCode、finishedAt和logFilePath
5. WHEN 执行超时时，THE 系统 SHALL 将任务执行状态更新为TIMEOUT
6. THE 任务执行 SHALL 独立存储agentId、executionNumber、status、logFilePath、exitCode、startedAt和finishedAt

### 需求 3：聚合任务状态

**用户故事：** 作为用户，我希望一目了然地看到多代理任务的整体状态，以便快速了解任务进度而无需检查单个执行。

#### 验收标准

1. WHEN 所有任务执行实例的状态为SUCCESS时，THE 任务 SHALL 显示聚合状态为ALL_SUCCESS
2. WHEN 至少一个任务执行的状态为FAILED或TIMEOUT且至少一个状态为SUCCESS时，THE 任务 SHALL 显示聚合状态为PARTIAL_SUCCESS
3. WHEN 所有任务执行实例的状态为FAILED或TIMEOUT时，THE 任务 SHALL 显示聚合状态为ALL_FAILED
4. WHEN 至少一个任务执行的状态为RUNNING或PULLED时，THE 任务 SHALL 显示聚合状态为IN_PROGRESS
5. WHEN 所有任务执行实例的状态为PENDING时，THE 任务 SHALL 显示聚合状态为PENDING
6. THE 任务 SHALL 显示执行进度比率（例如"3/5 已完成"），显示已完成执行数与总目标代理数

### 需求 4：任务列表显示增强

**用户故事：** 作为用户，我希望任务列表清晰显示多代理任务信息，以便区分单代理任务和多代理任务并了解其进度。

#### 验收标准

1. WHEN 在任务列表中显示任务时，THE Web界面 SHALL 显示目标代理数量
2. WHEN 显示多代理任务时，THE Web界面 SHALL 显示执行进度为"X/Y 已完成"，其中X是已完成执行数，Y是总目标代理数
3. WHEN 显示任务时，THE Web界面 SHALL 显示聚合状态并使用适当的视觉指示器
4. THE Web界面 SHALL 为所有任务显示任务名称、创建时间和创建者信息
5. THE Web界面 SHALL 提供查看详情、取消和重启任务的操作

### 需求 5：任务详情视图

**用户故事：** 作为用户，我希望查看任务所有执行实例的详细信息，以便了解哪些代理成功、哪些失败，并访问它们的日志。

#### 验收标准

1. WHEN 用户点击任务时，THE Web界面 SHALL 显示详情模态框，展示所有任务执行实例
2. WHEN 显示执行实例时，THE Web界面 SHALL 为每个实例显示agentId、status、startedAt、finishedAt和exitCode
3. WHEN 执行实例有日志时，THE Web界面 SHALL 提供按钮查看日志内容
4. WHEN 显示执行实例时，THE Web界面 SHALL 允许按状态过滤（全部、成功、失败、运行中、待处理）
5. THE Web界面 SHALL 在详情视图中显示任务的脚本内容、语言、超时时间和创建元数据

### 需求 6：按代理查看日志

**用户故事：** 作为用户，我希望分别查看每个代理执行的日志，以便排查特定代理的问题。

#### 验收标准

1. WHEN 用户点击执行实例的"查看日志"时，THE Web界面 SHALL 获取并显示该特定任务执行的日志内容
2. WHEN 显示日志内容时，THE Web界面 SHALL 显示agentId、执行状态和时间戳
3. WHEN 日志文件不存在或为空时，THE Web界面 SHALL 显示适当的消息
4. THE Web界面 SHALL 支持查看任何状态（SUCCESS、FAILED、TIMEOUT、RUNNING）的执行实例日志
5. THE Web界面 SHALL 允许将日志内容复制到剪贴板

### 需求 7：选择性执行重启

**用户故事：** 作为用户，我希望重启失败的执行而不影响成功的执行，以便高效重试失败而无需重新执行成功的代理。

#### 验收标准

1. WHEN 用户请求重启任务时，THE Web界面 SHALL 提供"重启所有执行"或"仅重启失败执行"的选项
2. WHEN 选择"仅重启失败执行"时，THE 系统 SHALL 仅为状态为FAILED或TIMEOUT的实例创建新的任务执行记录
3. WHEN 选择"重启所有执行"时，THE 系统 SHALL 为所有目标代理创建新的任务执行记录，不考虑之前的状态
4. WHEN 重启任务执行时，THE 系统 SHALL 递增executionNumber字段
5. THE 系统 SHALL 在创建新任务执行记录时保留之前的执行历史和日志
6. WHEN 重启执行时，THE 系统 SHALL 将新任务执行状态设置为PENDING

### 需求 8：单个执行取消

**用户故事：** 作为用户，我希望取消特定代理的执行而不取消整个任务，以便停止有问题的执行同时允许其他执行继续。

#### 验收标准

1. WHEN 用户取消特定任务执行时，THE 系统 SHALL 仅将该任务执行状态更新为CANCELLED
2. WHEN 任务执行被取消时，THE 系统 SHALL 通知相应代理停止执行（如果当前正在运行）
3. WHEN 用户取消整个任务时，THE 系统 SHALL 将所有状态为PENDING、PULLED或RUNNING的任务执行实例更新为CANCELLED
4. THE 系统 SHALL 不允许取消状态为SUCCESS、FAILED或TIMEOUT的任务执行实例
5. WHEN 任务执行被取消时，THE 系统 SHALL 记录finishedAt时间戳

### 需求 9：界面简化

**用户故事：** 作为用户，我希望有一个简化的任务界面，没有令人困惑的"批量任务"概念，以便专注于高效创建和管理多代理任务。

#### 验收标准

1. THE Web界面 SHALL 从任务管理页面移除"批量任务"标签页
2. THE Web界面 SHALL 仅显示"任务"标签页（原"普通任务"重命名为"任务"）
3. WHEN 创建任务时，THE Web界面 SHALL 使用多选组件进行代理选择，而非单选
4. THE Web界面 SHALL 移除所有批量任务创建和管理界面
5. THE Web界面 SHALL 保持现有任务操作（查看日志、重启、取消）并增强多代理支持

### 需求 10：API端点更新

**用户故事：** 作为开发者，我希望更新的API端点支持多代理任务操作，以便前端能够正确管理多目标任务。

#### 验收标准

1. WHEN 通过POST /api/web/tasks/create创建任务时，THE API接口 SHALL 在请求体中接受agentIds数组
2. THE API接口 SHALL 提供GET /api/web/tasks/{taskId}/executions端点，返回任务的所有任务执行记录
3. THE API接口 SHALL 提供GET /api/web/tasks/{taskId}/summary端点，返回聚合状态和执行进度
4. THE API接口 SHALL 提供POST /api/web/tasks/executions/{executionId}/cancel端点，取消特定执行实例
5. THE API接口 SHALL 提供POST /api/web/tasks/{taskId}/restart端点，支持"all"或"failed_only"重启模式选项
6. WHEN 通过GET /api/web/tasks查询任务列表时，THE API接口 SHALL 在响应中包含目标代理数量和执行进度

### 需求 11：数据模型一致性

**用户故事：** 作为开发者，我希望有一个一致的数据模型，正确表示任务和执行实例之间的关系，以便系统保持数据完整性。

#### 验收标准

1. THE 系统 SHALL 维护任务表，包含字段：taskId（主键）、taskName、scriptLang、scriptContent、timeoutSec、createdBy、createdAt
2. THE 系统 SHALL 维护任务执行表，包含字段：id（主键）、taskId（外键）、agentId、executionNumber、status、logFilePath、exitCode、pulledAt、startedAt、finishedAt
3. WHEN 删除任务时，THE 系统 SHALL 级联删除所有关联的任务执行记录
4. THE 系统 SHALL 在TaskExecution.taskId和Task.taskId之间强制执行外键约束
5. THE 系统 SHALL 确保executionNumber对于同一任务-代理组合的每次重启顺序递增
6. THE 系统 SHALL 在任务执行表的taskId和agentId上建立索引以提高查询性能

### 需求 12：向后兼容性

**用户故事：** 作为系统管理员，我希望现有的单代理任务无需修改即可继续工作，以便系统升级不会破坏现有功能。

#### 验收标准

1. WHEN 迁移现有任务时，THE 系统 SHALL 为每个现有的单代理任务创建一条任务执行记录
2. WHEN 显示已迁移任务时，THE Web界面 SHALL 将它们显示为目标数量为1的单代理任务
3. THE 系统 SHALL 在迁移期间保留所有现有任务数据，包括日志、状态和时间戳
4. WHEN 代理在迁移后拉取任务时，THE 系统 SHALL 继续使用新的任务执行模型工作
5. THE API接口 SHALL 为现有任务查询端点保持向后兼容性

### 需求 13：数据迁移策略

**用户故事：** 作为系统管理员，我希望有清晰的数据迁移策略，以便从当前实现平滑过渡到新的多目标架构。

#### 验收标准

1. THE 系统 SHALL 提供数据库迁移脚本，将现有Task表结构转换为新的Task和TaskExecution表结构
2. WHEN 执行迁移时，THE 系统 SHALL 为每个现有Task记录创建一条对应的TaskExecution记录
3. WHEN 迁移TaskExecution记录时，THE 系统 SHALL 将executionNumber设置为1，表示首次执行
4. THE 系统 SHALL 将现有Task表中的agentId、status、logFilePath、exitCode、pulledAt、startedAt、finishedAt字段迁移到TaskExecution表
5. THE 系统 SHALL 在Task表中保留taskId、taskName、scriptLang、scriptContent、timeoutSec、createdBy、createdAt字段
6. WHEN 迁移完成后，THE 系统 SHALL 验证所有数据完整性约束（外键、非空字段等）
7. THE 系统 SHALL 提供回滚脚本，以便在迁移失败时恢复到原始状态

### 需求 14：批量任务处理

**用户故事：** 作为系统管理员，我希望明确如何处理现有的批量任务功能，以便用户了解迁移路径。

#### 验收标准

1. THE 系统 SHALL 在数据库中保留现有BatchTask表和数据，但不在Web界面中显示
2. THE Web界面 SHALL 移除所有批量任务相关的UI组件（标签页、创建表单、列表视图）
3. THE API接口 SHALL 保留批量任务相关端点但标记为已弃用（@Deprecated注解）
4. THE 系统 SHALL 在日志中记录任何对已弃用批量任务API的调用
5. WHEN 用户尝试访问批量任务功能时，THE Web界面 SHALL 显示提示信息，说明该功能已被多代理任务替代
6. THE 系统 SHALL 提供文档说明如何将现有批量任务迁移为多代理任务

### 需求 15：代理拉取任务适配

**用户故事：** 作为代理开发者，我希望代理拉取任务的逻辑能够适配新的TaskExecution模型，以便代理能够正确执行任务并报告状态。

#### 验收标准

1. WHEN 代理调用拉取任务API时，THE API接口 SHALL 返回该代理的待处理TaskExecution记录（状态为PENDING）
2. THE API接口 SHALL 在响应中包含taskId、executionId、scriptContent、scriptLang、timeoutSec字段
3. WHEN 代理开始执行时，THE 代理 SHALL 调用API更新TaskExecution状态为RUNNING并传递executionId
4. WHEN 代理完成执行时，THE 代理 SHALL 调用API更新TaskExecution状态为SUCCESS或FAILED，并传递executionId、exitCode和logFilePath
5. THE API接口 SHALL 提供POST /api/agent/tasks/executions/{executionId}/status端点用于状态更新
6. THE 系统 SHALL 确保代理只能更新分配给它的TaskExecution记录（通过agentId验证）


## 解析器和序列化器需求

### 需求 16：任务配置序列化

**用户故事：** 作为开发者，我希望任务配置能够正确序列化和反序列化，以便任务数据一致地存储和检索。

#### 验收标准

1. WHEN 将任务存储到数据库时，THE 系统 SHALL 将任务配置序列化为JSON格式
2. WHEN 从数据库检索任务时，THE 系统 SHALL 将JSON配置反序列化为任务对象
3. THE 系统 SHALL 提供格式化器，将任务对象格式化回有效的JSON
4. FOR ALL 有效的任务对象，序列化然后反序列化然后再序列化 SHALL 产生等效的JSON输出（往返属性）
5. WHEN 序列化失败时，THE 系统 SHALL 返回描述性错误消息，指示哪个字段导致失败

## 实现优先级

### 高优先级（MVP必需）

1. **需求 1-3**：核心多代理任务创建和执行跟踪
   - 这是整个功能的基础，必须首先实现
   - 包括数据模型变更（Task和TaskExecution表）

2. **需求 11**：数据模型一致性
   - 确保数据库结构正确，支持后续功能

3. **需求 13**：数据迁移策略
   - 必须在部署前完成，确保现有数据不丢失

4. **需求 10**：API端点更新
   - 前端依赖这些API实现功能

### 中优先级（核心功能）

5. **需求 4-6**：UI显示和日志查看
   - 用户体验的关键部分

6. **需求 9**：界面简化
   - 移除批量任务标签页，简化用户界面

7. **需求 15**：代理拉取任务适配
   - 确保代理能够正确执行新模型的任务

### 低优先级（增强功能）

8. **需求 7-8**：选择性重启和取消
   - 提升用户体验，但不影响基本功能

9. **需求 14**：批量任务处理
   - 清理工作，可以在后期完成

10. **需求 12、16**：向后兼容性和序列化
    - 保障性需求，可以在测试阶段完善

## 技术实现注意事项

### 后端（Java Spring Boot）

1. **实体类变更**：
   - 修改`Task.java`：移除agentId、status等执行相关字段
   - 创建`TaskExecution.java`：包含执行实例的所有字段
   - 建立`@OneToMany`关系：Task → TaskExecution

2. **服务层**：
   - `TaskService`：处理任务创建、查询、聚合状态计算
   - `TaskExecutionService`：处理执行实例的状态更新、日志管理
   - 实现事务管理，确保任务和执行实例的原子性创建

3. **API控制器**：
   - `TaskController`：任务CRUD和聚合查询
   - `TaskExecutionController`：执行实例状态更新和日志查询
   - 使用DTO模式，避免直接暴露实体类

### 前端（React + Ant Design 5）

1. **组件重构**：
   - `TaskList.jsx`：显示任务列表，包含目标数量和进度
   - `TaskDetailModal.jsx`：显示任务详情和所有执行实例
   - `TaskCreateForm.jsx`：使用`Select`组件的`mode="multiple"`支持多选代理
   - `ExecutionLogModal.jsx`：显示单个执行实例的日志

2. **状态管理**：
   - 使用React Query或SWR管理服务器状态
   - 实现乐观更新，提升用户体验

3. **UI/UX改进**：
   - 使用`Progress`组件显示执行进度
   - 使用`Tag`组件显示聚合状态（不同颜色表示不同状态）
   - 使用`Table`组件的`expandable`属性显示执行实例详情

### 数据库迁移

1. **迁移脚本顺序**：
   ```sql
   -- 1. 创建TaskExecution表
   CREATE TABLE task_execution (
     id BIGINT PRIMARY KEY AUTO_INCREMENT,
     task_id BIGINT NOT NULL,
     agent_id VARCHAR(255) NOT NULL,
     execution_number INT DEFAULT 1,
     status VARCHAR(50),
     log_file_path VARCHAR(500),
     exit_code INT,
     pulled_at TIMESTAMP,
     started_at TIMESTAMP,
     finished_at TIMESTAMP,
     FOREIGN KEY (task_id) REFERENCES task(task_id) ON DELETE CASCADE
   );
   
   -- 2. 迁移现有Task数据到TaskExecution
   INSERT INTO task_execution (task_id, agent_id, execution_number, status, log_file_path, exit_code, pulled_at, started_at, finished_at)
   SELECT task_id, agent_id, 1, status, log_file_path, exit_code, pulled_at, started_at, finished_at
   FROM task
   WHERE agent_id IS NOT NULL;
   
   -- 3. 修改Task表结构（移除执行相关字段）
   ALTER TABLE task DROP COLUMN agent_id;
   ALTER TABLE task DROP COLUMN status;
   ALTER TABLE task DROP COLUMN log_file_path;
   ALTER TABLE task DROP COLUMN exit_code;
   ALTER TABLE task DROP COLUMN pulled_at;
   ALTER TABLE task DROP COLUMN started_at;
   ALTER TABLE task DROP COLUMN finished_at;
   ALTER TABLE task DROP COLUMN execution_count;
   
   -- 4. 创建索引
   CREATE INDEX idx_task_execution_task_id ON task_execution(task_id);
   CREATE INDEX idx_task_execution_agent_id ON task_execution(agent_id);
   CREATE INDEX idx_task_execution_status ON task_execution(status);
   ```

2. **回滚脚本**：
   - 保留原始Task表的备份
   - 提供脚本将TaskExecution数据合并回Task表

## 验收测试场景

### 场景 1：创建单代理任务

1. 用户选择1个代理
2. 填写任务名称、脚本内容、语言、超时时间
3. 提交创建
4. 验证：创建1条Task记录和1条TaskExecution记录
5. 验证：任务列表显示"1个目标代理"

### 场景 2：创建多代理任务

1. 用户选择3个代理（agent-1, agent-2, agent-3）
2. 填写任务信息并提交
3. 验证：创建1条Task记录和3条TaskExecution记录
4. 验证：任务列表显示"3个目标代理"和"0/3 已完成"

### 场景 3：执行进度跟踪

1. 创建包含3个代理的任务
2. agent-1拉取任务并开始执行
3. 验证：任务状态显示"进行中"，进度"0/3 已完成"
4. agent-1完成执行（成功）
5. 验证：进度更新为"1/3 已完成"
6. agent-2完成执行（成功），agent-3完成执行（失败）
7. 验证：任务状态显示"部分成功"，进度"3/3 已完成"

### 场景 4：查看执行详情

1. 点击多代理任务
2. 验证：显示详情模态框，包含3个执行实例
3. 验证：每个实例显示代理ID、状态、时间戳
4. 点击某个实例的"查看日志"
5. 验证：显示该代理的日志内容

### 场景 5：选择性重启

1. 创建包含3个代理的任务，其中2个成功、1个失败
2. 点击"重启"按钮，选择"仅重启失败执行"
3. 验证：仅为失败的代理创建新的TaskExecution记录
4. 验证：新记录的executionNumber为2
5. 验证：成功的执行实例保持不变

### 场景 6：数据迁移验证

1. 在迁移前创建10个单代理任务
2. 执行数据迁移脚本
3. 验证：10条Task记录保留，创建10条TaskExecution记录
4. 验证：所有日志、状态、时间戳正确迁移
5. 验证：Web界面正确显示迁移后的任务

## 风险和缓解措施

### 风险 1：数据迁移失败

**影响**：现有任务数据丢失或损坏

**缓解措施**：
- 在生产环境迁移前，在测试环境完整测试迁移脚本
- 迁移前完整备份数据库
- 提供回滚脚本
- 分阶段迁移：先迁移部分数据验证，再全量迁移

### 风险 2：代理兼容性问题

**影响**：现有代理无法拉取和执行新模型的任务

**缓解措施**：
- 保持代理API向后兼容
- 提供代理升级文档和脚本
- 在测试环境验证代理兼容性
- 支持新旧API并存一段时间

### 风险 3：性能问题

**影响**：大量执行实例导致查询性能下降

**缓解措施**：
- 在TaskExecution表的关键字段上建立索引
- 实现分页查询，避免一次加载过多数据
- 考虑使用缓存（Redis）存储聚合状态
- 定期归档历史执行记录

### 风险 4：用户学习成本

**影响**：用户不理解新的多代理任务模型

**缓解措施**：
- 提供用户文档和操作指南
- 在UI中添加提示信息和帮助文本
- 提供示例和最佳实践
- 收集用户反馈并持续改进

## 成功标准

1. **功能完整性**：所有15个需求的验收标准全部通过
2. **性能指标**：
   - 任务列表查询响应时间 < 500ms（100条记录）
   - 任务详情查询响应时间 < 300ms（包含所有执行实例）
   - 支持单个任务最多100个目标代理
3. **数据完整性**：迁移后所有现有任务数据完整无损
4. **用户体验**：用户能够在5分钟内学会创建和管理多代理任务
5. **代码质量**：单元测试覆盖率 > 80%，所有API端点有集成测试

## 后续阶段预览

### 第二阶段：批量任务编排（Workflow）

在第一阶段完成后，将实现真正的批量任务功能：

1. **工作流设计器**：可视化拖拽式任务编排
2. **依赖关系管理**：任务之间的顺序和条件依赖
3. **工作流执行引擎**：支持串行、并行、条件分支
4. **工作流监控**：实时查看工作流执行状态和进度

这将使批量任务成为真正的"任务编排"工具，而非简单的"多代理执行"。
