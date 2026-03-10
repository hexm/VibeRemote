# 任务重启逻辑修复 - 2026-03-10

## 问题描述

### 重大逻辑错误
用户发现了一个重大问题：任务执行失败后，每次重启任务，执行节点数量都会增加。这与原始设计不符。

**原始设计**：
- 任务创建后，执行节点数量固定
- 重启任务只是让失败的任务项重新执行
- 不应该增加新的执行节点

**实际行为**：
- 每次重启都会创建新的执行实例
- 导致执行节点数量不断增加
- 违背了任务设计的初衷

## 根本原因

在`TaskService.restartTask()`方法中，重启逻辑错误地为每个需要重启的执行创建了**新的执行实例**：

```java
// 错误的逻辑 - 创建新的执行实例
for (TaskExecution execution : toRestart) {
    Integer nextNumber = taskExecutionService.getNextExecutionNumber(taskId, execution.getAgentId());
    taskExecutionService.createExecution(taskId, execution.getAgentId(), nextNumber);
    newExecutionCount++;
}
```

这导致：
1. 原有的失败执行实例仍然存在
2. 每次重启都会创建新的执行实例
3. 执行节点数量不断增加

## 解决方案

### 修复策略
将"创建新执行实例"改为"重置现有执行实例状态"：

```java
// 正确的逻辑 - 重置现有执行实例状态
for (TaskExecution execution : toRestart) {
    // 重置执行状态
    execution.setStatus("PENDING");
    execution.setStartTime(null);
    execution.setEndTime(null);
    execution.setResult(null);
    execution.setErrorMessage(null);
    execution.setUpdatedAt(LocalDateTime.now());
    execution.setPulledAt(null);
    execution.setAckedAt(null);
    execution.setLogFilePath(null);
    
    taskExecutionRepository.save(execution);
    restartedCount++;
}
```

### 重置的字段
- `status`: 重置为"PENDING"，等待重新执行
- `startTime`: 清空开始时间
- `endTime`: 清空结束时间
- `result`: 清空执行结果
- `errorMessage`: 清空错误信息
- `updatedAt`: 更新为当前时间
- `pulledAt`: 清空拉取时间
- `ackedAt`: 清空确认时间
- `logFilePath`: 清空日志文件路径

### 消息更新
将提示消息从"创建了X个新的执行实例"改为"重置了X个执行实例的状态"，更准确地反映实际操作。

## 修复效果

### 1. 执行节点数量保持不变
- 重启任务不再增加执行节点
- 保持任务创建时的节点数量
- 符合原始设计意图

### 2. 重启行为正确
- **ALL模式**：重置所有执行实例状态，重新执行所有节点
- **FAILED_ONLY模式**：只重置失败和超时的执行实例，重新执行失败的节点

### 3. 数据一致性
- 不会产生重复的执行记录
- 保持任务执行历史的完整性
- 避免数据库中的冗余记录

### 4. 用户体验改善
- 重启后执行节点数量符合预期
- 任务状态和进度显示正确
- 避免用户困惑

## 测试建议

### 1. 基本重启测试
1. 创建一个多节点任务（如3个Agent）
2. 让部分执行失败
3. 使用"重启"功能
4. 验证执行节点数量仍为3个，而不是6个

### 2. 重启模式测试
1. **ALL模式**：验证所有执行实例都被重置并重新执行
2. **FAILED_ONLY模式**：验证只有失败的执行实例被重置

### 3. 多次重启测试
1. 连续多次重启同一个任务
2. 验证执行节点数量始终保持不变
3. 验证每次重启都能正确重置状态

### 4. 状态验证测试
1. 验证重启后执行实例的所有字段都被正确重置
2. 验证重启后任务可以正常执行
3. 验证重启后日志记录正确

## 影响范围

### 修改的文件
- `server/src/main/java/com/example/lightscript/server/service/TaskService.java`

### 修改的方法
- `TaskService.restartTask(String taskId, TaskModels.RestartMode mode)`

### API行为变化
- `/api/web/tasks/{taskId}/restart` 接口行为更正
- 返回消息从"创建了X个新的执行实例"改为"重置了X个执行实例的状态"

## 重要性

这是一个**重大的逻辑修复**，因为：

1. **数据完整性**：避免了不必要的数据冗余
2. **用户体验**：符合用户的预期行为
3. **系统设计**：恢复了原始的任务设计逻辑
4. **资源管理**：避免了不必要的执行实例创建

这个修复确保了任务重启功能按照预期工作，维护了系统的数据一致性和用户体验。