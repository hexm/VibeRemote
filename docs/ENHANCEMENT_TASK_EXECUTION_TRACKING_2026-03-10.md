# 任务执行跟踪增强 - 2026-03-10

## 问题描述

用户测试后发现了两个重要问题：

### 1. 执行次数问题
- **问题**：重启后，执行次数没有变化，永远是第一次
- **期望**：每次重启应该递增执行次数，显示这是第几次执行

### 2. 任务级别时间跟踪缺失
- **问题**：任务缺少开始时间、结束时间、执行次数等重要信息
- **期望**：任务应该有自己的开始时间、结束时间、执行次数

## 解决方案

### 1. 数据库结构增强

#### Task表新增字段
```sql
ALTER TABLE tasks ADD COLUMN execution_count INTEGER DEFAULT 1;
ALTER TABLE tasks ADD COLUMN started_at TIMESTAMP;
ALTER TABLE tasks ADD COLUMN finished_at TIMESTAMP;
```

- `execution_count`: 任务执行次数，重启时递增
- `started_at`: 任务开始时间（首次执行或重启时更新）
- `finished_at`: 任务结束时间

#### 数据库迁移
- 创建了 `V8__add_task_execution_tracking.sql` 迁移脚本
- 为现有任务设置默认执行次数为1

### 2. 后端逻辑修复

#### Task实体增强
```java
@Column(name = "execution_count")
private Integer executionCount = 1; // 任务执行次数，重启时递增

@Column(name = "started_at")
private LocalDateTime startedAt; // 任务开始时间

@Column(name = "finished_at")
private LocalDateTime finishedAt; // 任务结束时间
```

#### 重启逻辑修复
```java
// 递增执行实例的执行次数
execution.setExecutionNumber(execution.getExecutionNumber() + 1);

// 更新任务级别的执行次数和时间
task.setExecutionCount(task.getExecutionCount() == null ? 2 : task.getExecutionCount() + 1);
task.setStartedAt(LocalDateTime.now());
task.setFinishedAt(null); // 清空结束时间，因为任务重新开始
```

#### 状态更新时的时间跟踪
```java
// 任务开始运行时设置开始时间
if ("RUNNING".equals(newStatus) && task.getStartedAt() == null) {
    task.setStartedAt(LocalDateTime.now());
}

// 任务完成时设置结束时间
if (isCompletedStatus(newStatus) && task.getFinishedAt() == null) {
    task.setFinishedAt(LocalDateTime.now());
}
```

### 3. 前端显示增强

#### 新增表格列
- **执行次数**：显示"第X次"，用cyan颜色的Tag
- **开始时间**：显示任务开始执行的时间
- **结束时间**：显示任务完成的时间

```jsx
{
  title: '执行次数',
  dataIndex: 'executionCount',
  key: 'executionCount',
  width: 100,
  render: (count) => <Tag color="cyan">第 {count || 1} 次</Tag>,
},
{
  title: '开始时间',
  dataIndex: 'startedAt',
  key: 'startedAt',
  width: 160,
  render: (time) => <Text className="text-xs">{time ? formatDateTime(time) : '-'}</Text>,
},
{
  title: '结束时间',
  dataIndex: 'finishedAt',
  key: 'finishedAt',
  width: 160,
  render: (time) => <Text className="text-xs">{time ? formatDateTime(time) : '-'}</Text>,
}
```

## 修复效果

### 1. 执行次数正确跟踪
- ✅ 首次创建任务：执行次数为1
- ✅ 第一次重启：执行次数变为2
- ✅ 第二次重启：执行次数变为3
- ✅ 以此类推...

### 2. 执行实例次数递增
- ✅ 每个执行实例的`executionNumber`在重启时递增
- ✅ 可以区分同一Agent上的不同次执行

### 3. 任务时间跟踪
- ✅ **开始时间**：任务首次运行或重启时更新
- ✅ **结束时间**：任务完成（成功/失败/停止/取消）时设置
- ✅ **重启时**：开始时间更新，结束时间清空

### 4. 前端显示完整
- ✅ 用户可以看到任务执行了多少次
- ✅ 用户可以看到任务的开始和结束时间
- ✅ 时间显示格式友好（使用formatDateTime）

## 业务逻辑

### 执行次数逻辑
1. **任务创建**：executionCount = 1
2. **任务重启**：executionCount += 1
3. **执行实例**：executionNumber += 1（每个Agent的执行实例独立递增）

### 时间跟踪逻辑
1. **开始时间**：
   - 任务状态变为RUNNING时设置（如果还没有设置）
   - 重启时更新为当前时间
2. **结束时间**：
   - 任务状态变为完成状态时设置（SUCCESS/FAILED/PARTIAL_SUCCESS/STOPPED/CANCELLED）
   - 重启时清空，等待新的完成时间

### 重启行为
1. **保持节点数量不变**（之前修复的问题）
2. **递增执行次数**（本次修复）
3. **更新时间跟踪**（本次修复）
4. **重置执行实例状态**（保持之前的修复）

## 测试场景

### 1. 基本执行次数测试
1. 创建任务 → 执行次数显示"第1次"
2. 重启任务 → 执行次数显示"第2次"
3. 再次重启 → 执行次数显示"第3次"

### 2. 时间跟踪测试
1. 创建任务 → 开始时间和结束时间为空
2. 启动任务 → 开始时间设置，结束时间仍为空
3. 任务完成 → 结束时间设置
4. 重启任务 → 开始时间更新，结束时间清空
5. 任务再次完成 → 结束时间重新设置

### 3. 执行实例次数测试
1. 查看执行实例详情
2. 验证每次重启后executionNumber递增
3. 验证不同Agent的executionNumber独立计算

## 影响范围

### 修改的文件
- `server/src/main/java/com/example/lightscript/server/entity/Task.java`
- `server/src/main/java/com/example/lightscript/server/service/TaskService.java`
- `server/src/main/resources/db/migration/V8__add_task_execution_tracking.sql`
- `web-modern/src/pages/Tasks.jsx`

### 数据库变更
- 新增3个字段到tasks表
- 自动迁移，不影响现有数据

### API变更
- Task相关API返回的数据包含新字段
- 前端可以直接使用这些新字段

## 重要性

这个增强解决了任务执行跟踪的完整性问题：

1. **用户体验**：用户可以清楚地看到任务执行了多少次
2. **时间跟踪**：提供完整的任务执行时间信息
3. **数据完整性**：任务和执行实例的次数都正确跟踪
4. **运维监控**：便于监控任务的执行历史和性能

这个修复确保了任务执行跟踪功能的完整性和准确性。