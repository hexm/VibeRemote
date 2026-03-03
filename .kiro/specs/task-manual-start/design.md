# 任务手动启动功能设计文档

## 1. 设计概述
本功能在现有多目标任务系统基础上，增加任务生命周期管理能力，允许用户控制任务的启动时机。

## 2. 架构设计

### 2.1 核心概念
- **任务状态（Task Status）**: 任务本身的生命周期状态
- **执行状态（Execution Status）**: 从执行实例统计得出的执行情况

### 2.2 状态转换图
```
                    start()
DRAFT ──────────────────────> PENDING
                                 │
                                 │ (执行实例状态变化)
                                 ↓
                    ┌────────> RUNNING
                    │            │
                    │            │ (所有执行实例完成)
                    │            ↓
                    │      ┌──> SUCCESS
                    │      │
                    │      ├──> FAILED
                    │      │
                    │      ├──> PARTIAL_SUCCESS
                    │      │
    stop()          │      └──> CANCELLED
      ↓             │
   STOPPED <────────┘
      │
      │ restart()
      ↓
   PENDING
```

说明：
- DRAFT是唯一手动设置的初始状态
- 其他状态都是根据执行实例状态自动计算
- stop()操作可以在PENDING/RUNNING状态时执行
- restart()可以在SUCCESS/FAILED/PARTIAL_SUCCESS/STOPPED状态时执行

## 3. 数据模型设计

### 3.1 Task表变更
```sql
ALTER TABLE task ADD COLUMN task_status VARCHAR(20) DEFAULT 'ACTIVE';
ALTER TABLE task ADD INDEX idx_task_status (task_status);
```

字段说明：
- `task_status`: 任务状态（DRAFT/ACTIVE/STOPPED）
- 默认值ACTIVE保持向后兼容

### 3.2 状态说明
| 状态 | 说明 | 是否有执行实例 | Agent是否可拉取 | 如何产生 |
|------|------|---------------|----------------|----------|
| DRAFT | 草稿 | 否 | 否 | 创建时autoStart=false |
| PENDING | 待执行 | 是（全部PENDING） | 是 | 启动任务/重启任务 |
| RUNNING | 执行中 | 是（至少一个RUNNING） | 是 | 执行实例状态变化 |
| SUCCESS | 成功 | 是（全部SUCCESS） | 否 | 执行实例状态变化 |
| FAILED | 失败 | 是（全部FAILED） | 否 | 执行实例状态变化 |
| PARTIAL_SUCCESS | 部分成功 | 是（部分SUCCESS部分FAILED） | 否 | 执行实例状态变化 |
| STOPPED | 已停止 | 是（已取消） | 否 | 手动停止 |
| CANCELLED | 已取消 | 是（全部CANCELLED） | 否 | 执行实例状态变化 |

## 4. API设计

### 4.1 创建任务（修改现有接口）
```java
@PostMapping("/tasks/create")
public ResponseEntity<Map<String, Object>> createTask(
    @RequestParam List<String> agentIds,
    @RequestParam String taskName,
    @RequestParam(defaultValue = "true") Boolean autoStart,  // 新增参数
    @RequestBody TaskSpec taskSpec
)
```

**逻辑变更**：
1. 创建Task实体，设置task_status
   - autoStart=true: task_status=PENDING，立即创建执行实例
   - autoStart=false: task_status=DRAFT，不创建执行实例
2. 只有autoStart=true时才创建执行实例
3. 返回响应包含taskStatus字段

### 4.2 启动任务（新增接口）
```java
@PostMapping("/tasks/{taskId}/start")
public ResponseEntity<Map<String, Object>> startTask(
    @PathVariable String taskId
)
```

**实现逻辑**：
1. 查询任务，验证状态为DRAFT
2. 为所有目标代理创建执行实例（状态=PENDING）
3. 任务状态自动变为PENDING（由状态计算逻辑更新）
4. 返回创建的执行实例数量

**异常处理**：
- 任务不存在：404
- 任务状态不是DRAFT：400（任务已启动）

### 4.3 停止任务（新增接口）
```java
@PostMapping("/tasks/{taskId}/stop")
public ResponseEntity<Map<String, Object>> stopTask(
    @PathVariable String taskId
)
```

**实现逻辑**：
1. 查询任务，验证状态为PENDING或RUNNING
2. 取消所有未完成的执行实例（PENDING/PULLED/RUNNING）
3. 任务状态自动变为STOPPED（由状态计算逻辑更新）
4. 返回取消的执行实例数量

**异常处理**：
- 任务不存在：404
- 任务状态不允许停止：400（如DRAFT、SUCCESS、FAILED等）

### 4.4 重启任务（修改现有接口）
保持现有逻辑，但增加状态检查：
- 可以重启SUCCESS/FAILED/PARTIAL_SUCCESS/STOPPED状态的任务
- 重启时创建新的执行实例
- 任务状态自动变为PENDING

## 5. Service层设计

### 5.1 TaskService新增方法
```java
public interface TaskService {
    // 启动任务
    StartTaskResponse startTask(String taskId);
    
    // 停止任务
    StopTaskResponse stopTask(String taskId);
    
    // 计算任务状态（根据执行实例状态）
    String calculateTaskStatus(String taskId);
    
    // 更新任务状态
    void updateTaskStatus(String taskId);
    
    // 修改现有方法签名
    CreateTaskResponse createMultiAgentTask(
        List<String> agentIds, 
        TaskSpec taskSpec, 
        String createdBy,
        boolean autoStart  // 新增参数
    );
}
```

### 5.2 状态计算逻辑
```java
public String calculateTaskStatus(String taskId) {
    Task task = taskRepository.findById(taskId)
        .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
    
    // DRAFT状态不变
    if ("DRAFT".equals(task.getTaskStatus())) {
        return "DRAFT";
    }
    
    // 获取所有执行实例
    List<TaskExecution> executions = taskExecutionRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
    
    if (executions.isEmpty()) {
        return "DRAFT";
    }
    
    // 统计各状态数量
    long totalCount = executions.size();
    long pendingCount = executions.stream().filter(e -> "PENDING".equals(e.getStatus())).count();
    long pulledCount = executions.stream().filter(e -> "PULLED".equals(e.getStatus())).count();
    long runningCount = executions.stream().filter(e -> "RUNNING".equals(e.getStatus())).count();
    long successCount = executions.stream().filter(e -> "SUCCESS".equals(e.getStatus())).count();
    long failedCount = executions.stream().filter(e -> "FAILED".equals(e.getStatus())).count();
    long cancelledCount = executions.stream().filter(e -> "CANCELLED".equals(e.getStatus())).count();
    
    // 状态判断逻辑
    if (runningCount > 0 || pulledCount > 0) {
        return "RUNNING";
    }
    
    if (successCount == totalCount) {
        return "SUCCESS";
    }
    
    if (failedCount == totalCount) {
        return "FAILED";
    }
    
    if (cancelledCount == totalCount) {
        return "CANCELLED";
    }
    
    if (successCount > 0 && failedCount > 0) {
        return "PARTIAL_SUCCESS";
    }
    
    if (pendingCount == totalCount) {
        return "PENDING";
    }
    
    // 默认返回PENDING
    return "PENDING";
}
```

### 5.3 状态更新触发时机
任务状态需要在以下时机自动更新：
1. 创建执行实例后
2. 执行实例状态变更后（ACK、完成、取消）
3. 启动任务后
4. 停止任务后
5. 重启任务后

### 5.4 响应DTO
```java
@Data
public class StartTaskResponse {
    private String taskId;
    private String taskStatus;  // 应该是PENDING
    private Integer executionCount;
    private String message;
}

@Data
public class StopTaskResponse {
    private String taskId;
    private String taskStatus;  // 应该是STOPPED或CANCELLED
    private Integer cancelledCount;
    private String message;
}
```

## 6. Agent拉取任务逻辑修改

### 6.1 pullTasks方法
```java
public List<TaskSpec> pullTasks(String agentId, int maxTasks) {
    // 只查询非DRAFT、非STOPPED、非CANCELLED状态任务的PENDING执行实例
    List<TaskExecution> pendingExecutions = 
        taskExecutionRepository.findPendingExecutionsForActiveTasks(agentId);
    
    // ... 现有逻辑
}
```

### 6.2 Repository新增查询
```java
@Query("SELECT te FROM TaskExecution te " +
       "JOIN Task t ON te.taskId = t.taskId " +
       "WHERE te.agentId = :agentId " +
       "AND te.status = 'PENDING' " +
       "AND t.taskStatus NOT IN ('DRAFT', 'STOPPED', 'CANCELLED') " +
       "ORDER BY te.createdAt ASC")
List<TaskExecution> findPendingExecutionsForActiveTasks(
    @Param("agentId") String agentId
);
```

## 7. 前端设计

### 7.1 创建任务界面
- 增加"立即启动"复选框（默认勾选）
- 提示文字："取消勾选后，任务将保存为草稿，需要手动启动"

### 7.2 任务列表界面
- 显示任务状态标签（草稿/待执行/执行中/成功/失败/部分成功/已停止）
- 增加状态筛选器
- 操作按钮根据状态显示：
  - DRAFT: 显示"启动"按钮
  - PENDING/RUNNING: 显示"停止"、"重启"按钮
  - SUCCESS/FAILED/PARTIAL_SUCCESS: 显示"重启"按钮
  - STOPPED: 显示"重启"按钮

### 7.3 任务详情界面
- 显示任务状态
- 显示执行状态（从执行实例统计）
- 提供启动/停止/重启操作

## 8. 数据库迁移

### 8.1 迁移脚本
```sql
-- V5__task_manual_start_support.sql

-- 添加任务状态字段
ALTER TABLE task ADD COLUMN task_status VARCHAR(20) DEFAULT 'PENDING';

-- 添加索引
ALTER TABLE task ADD INDEX idx_task_status (task_status);

-- 更新现有数据：根据执行实例状态计算任务状态
-- 有执行实例的任务，根据执行实例状态设置
UPDATE task t 
SET t.task_status = (
    CASE 
        WHEN NOT EXISTS (SELECT 1 FROM task_execution te WHERE te.task_id = t.task_id) 
            THEN 'DRAFT'
        WHEN EXISTS (SELECT 1 FROM task_execution te WHERE te.task_id = t.task_id AND te.status = 'RUNNING')
            THEN 'RUNNING'
        WHEN (SELECT COUNT(*) FROM task_execution te WHERE te.task_id = t.task_id AND te.status = 'SUCCESS') = 
             (SELECT COUNT(*) FROM task_execution te WHERE te.task_id = t.task_id)
            THEN 'SUCCESS'
        WHEN (SELECT COUNT(*) FROM task_execution te WHERE te.task_id = t.task_id AND te.status = 'FAILED') = 
             (SELECT COUNT(*) FROM task_execution te WHERE te.task_id = t.task_id)
            THEN 'FAILED'
        WHEN EXISTS (SELECT 1 FROM task_execution te WHERE te.task_id = t.task_id AND te.status IN ('SUCCESS', 'FAILED'))
            THEN 'PARTIAL_SUCCESS'
        ELSE 'PENDING'
    END
);
```

## 9. 测试设计

### 9.1 单元测试
- TaskService.startTask() 测试
- TaskService.stopTask() 测试
- TaskService.calculateTaskStatus() 测试（状态计算逻辑）
- TaskService.createMultiAgentTask() 带autoStart参数测试

### 9.2 集成测试
- 创建草稿任务 -> 启动 -> 验证状态变为PENDING
- 创建任务 -> 执行 -> 验证状态自动更新（PENDING -> RUNNING -> SUCCESS）
- 创建任务 -> 停止 -> 验证状态变为STOPPED
- Agent拉取任务 -> 验证只能拉取可执行状态的任务

### 9.3 端到端测试
- 前端创建草稿任务 -> 手动启动 -> Agent执行
- 前端停止任务 -> 验证Agent无法拉取

## 10. 兼容性考虑

### 10.1 向后兼容
- autoStart参数默认为true，保持现有行为
- 现有任务根据执行实例状态自动计算task_status
- 现有API调用不受影响

### 10.2 数据迁移
- 现有任务的task_status根据执行实例状态计算
- 没有执行实例的任务设为DRAFT
- 有执行实例的任务根据执行实例状态设置相应状态

## 11. 性能考虑

### 11.1 查询优化
- task_status字段添加索引
- Agent拉取任务时增加task_status条件，减少查询范围

### 11.2 并发控制
- 启动/停止操作使用事务保证原子性
- 状态变更使用乐观锁防止并发冲突

## 12. 安全考虑

### 12.1 权限控制
- 只有任务创建者或管理员可以启动/停止任务
- 使用Spring Security验证权限

### 12.2 参数验证
- 验证taskId存在性
- 验证状态转换合法性

## 13. 监控和日志

### 13.1 日志记录
- 记录任务状态变更：DRAFT -> PENDING -> RUNNING -> SUCCESS/FAILED
- 记录启动/停止操作的用户和时间
- 记录执行实例创建/取消数量
- 记录状态自动更新事件

### 13.2 监控指标
- 各状态任务数量（DRAFT/PENDING/RUNNING/SUCCESS/FAILED等）
- 启动/停止操作频率
- 状态转换频率

## 14. 实施计划

### Phase 1: 后端核心功能
1. 数据库迁移脚本
2. Task实体增加taskStatus字段
3. TaskService增加start/stop方法
4. WebController增加start/stop接口
5. 修改createTask接口支持autoStart参数
6. 修改Agent拉取逻辑

### Phase 2: 前端界面
1. 创建任务界面增加"立即启动"选项
2. 任务列表显示状态标签
3. 任务列表增加状态筛选器
4. 任务详情增加启动/停止按钮

### Phase 3: 测试和文档
1. 单元测试
2. 集成测试
3. 端到端测试
4. 用户文档更新

## 15. 风险和缓解

### 风险1: 数据迁移失败
**缓解措施**: 
- 提供回滚脚本
- 在测试环境充分测试
- 备份生产数据

### 风险2: Agent拉取逻辑变更影响现有Agent
**缓解措施**:
- 保持API兼容性
- 现有任务默认为ACTIVE状态
- 充分测试Agent拉取逻辑

### 风险3: 并发操作导致状态不一致
**缓解措施**:
- 使用事务保证原子性
- 添加乐观锁
- 记录详细日志便于排查
