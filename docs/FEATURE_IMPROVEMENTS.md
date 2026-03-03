# 任务手动启动功能 - 功能改进报告

## 改进日期
2026-02-28

## 改进内容

### 改进1：执行成功的任务不能重启 ✅

**需求**：执行成功的任务不应该显示重启按钮，避免误操作。

**实现**：
- 修改了任务列表的操作按钮显示逻辑
- SUCCESS状态的任务不再显示重启按钮
- 只有FAILED、PARTIAL_SUCCESS、STOPPED、CANCELLED状态的任务才显示重启按钮

**代码变更**：
```javascript
// 修改前：SUCCESS状态也显示重启按钮
{(record.taskStatus === 'SUCCESS' || record.taskStatus === 'FAILED' || ...) && (
  <Button icon={<RedoOutlined />} />
)}

// 修改后：SUCCESS状态不显示重启按钮
{(record.taskStatus === 'FAILED' || 
  record.taskStatus === 'PARTIAL_SUCCESS' || 
  record.taskStatus === 'STOPPED' ||
  record.taskStatus === 'CANCELLED') && (
  <Button icon={<RedoOutlined />} />
)}
```

**测试验证**：
- ✅ 成功的任务不显示重启按钮
- ✅ 失败的任务显示重启按钮
- ✅ 部分成功的任务显示重启按钮
- ✅ 已停止的任务显示重启按钮
- ✅ 已取消的任务显示重启按钮

### 改进2：启动任务时不需要选择执行节点，默认全部执行 ✅

**需求**：启动草稿任务时，应该自动使用创建任务时选择的代理节点，不需要用户重新选择。

**实现方案**：
1. 在Task表中添加`target_agent_ids`字段，保存目标代理ID列表（逗号分隔）
2. 创建任务时保存目标代理列表
3. 启动任务时，如果没有提供agentIds参数，使用保存的代理列表
4. 前端简化启动任务的逻辑，不再显示代理选择对话框

**数据库变更**：
- 创建了V6迁移脚本
- 添加了`target_agent_ids`字段（VARCHAR(2000)）
- 从执行实例中回填现有任务的目标代理列表

**后端变更**：
1. Task实体增加`targetAgentIds`字段
2. `createMultiAgentTask`方法保存目标代理列表
3. `startTask`方法支持不传递agentIds参数，自动使用保存的列表
4. WebController的`startTask`接口的agentIds参数改为可选

**前端变更**：
- 简化了`handleStartTask`函数
- 移除了代理选择对话框
- 直接调用启动API，不传递agentIds参数
- 显示简单的确认对话框

**测试验证**：
```bash
# 创建草稿任务
curl -X POST "http://localhost:8080/api/web/tasks/create?agentIds=xxx&taskName=test&autoStart=false"
# 结果：taskStatus: "DRAFT", targetAgentIds: "xxx"

# 启动任务（不传递agentIds）
curl -X POST "http://localhost:8080/api/web/tasks/{taskId}/start"
# 结果：成功启动，使用保存的代理列表
```

- ✅ 创建任务时保存目标代理列表
- ✅ 启动任务时自动使用保存的列表
- ✅ 不需要用户重新选择代理
- ✅ 前端界面更简洁

### 改进3：启动的图标和重启的图标不要用同一个 ✅

**需求**：启动按钮和重启按钮应该使用不同的图标，便于区分。

**实现**：
- 启动按钮：使用`PlayCircleOutlined`图标（播放图标）
- 重启按钮：使用`RedoOutlined`图标（重做图标）

**代码变更**：
```javascript
// 启动按钮
<Button icon={<PlayCircleOutlined />} />  // 播放图标

// 重启按钮
<Button icon={<RedoOutlined />} />  // 重做图标
```

**视觉效果**：
- 启动按钮：绿色的播放图标（▶️）
- 重启按钮：橙色的重做图标（↻）

**测试验证**：
- ✅ 草稿任务显示播放图标
- ✅ 失败任务显示重做图标
- ✅ 两个图标明显不同，易于区分

## 文件变更清单

### 后端文件
1. `server/src/main/java/com/example/lightscript/server/entity/Task.java`
   - 添加了`targetAgentIds`字段

2. `server/src/main/java/com/example/lightscript/server/service/TaskService.java`
   - 修改了`createMultiAgentTask`方法，保存目标代理列表
   - 修改了`startTask`方法，支持不传递agentIds参数

3. `server/src/main/java/com/example/lightscript/server/web/WebController.java`
   - 修改了`startTask`接口，agentIds参数改为可选

4. `server/src/main/resources/db/migration/V6__add_target_agent_ids.sql`
   - 新增数据库迁移脚本

### 前端文件
1. `web-modern/src/pages/Tasks.jsx`
   - 修改了操作按钮的显示逻辑（改进1）
   - 简化了`handleStartTask`函数（改进2）
   - 修改了启动按钮的图标（改进3）

## 测试结果

### 功能测试
- ✅ 成功的任务不显示重启按钮
- ✅ 失败的任务显示重启按钮
- ✅ 创建草稿任务保存目标代理列表
- ✅ 启动任务自动使用保存的代理列表
- ✅ 启动按钮使用播放图标
- ✅ 重启按钮使用重做图标

### 回归测试
- ✅ 创建草稿任务
- ✅ 启动草稿任务
- ✅ 创建立即启动的任务
- ✅ 停止运行中的任务
- ✅ 重启失败的任务
- ✅ 查看任务详情
- ✅ 状态筛选

## 用户体验改进

### 改进前
1. 成功的任务也显示重启按钮，容易误操作
2. 启动任务时需要重新选择代理节点，操作繁琐
3. 启动和重启使用相同的图标，不易区分

### 改进后
1. 成功的任务不显示重启按钮，避免误操作
2. 启动任务自动使用创建时的代理列表，操作简单
3. 启动和重启使用不同的图标，清晰明了

## 部署说明

### 后端部署
1. 停止现有服务
2. 备份数据库
3. 部署新的jar包
4. 启动服务（Flyway会自动执行V6迁移脚本）

### 前端部署
1. 重新构建：`npm run build`
2. 部署新的构建文件

### 数据迁移
- V6迁移脚本会自动执行
- 现有任务的目标代理列表会从执行实例中回填
- 无数据丢失风险

## 总结

三个改进都已成功实现并测试通过：
1. ✅ 成功的任务不能重启
2. ✅ 启动任务不需要选择节点
3. ✅ 启动和重启使用不同图标

这些改进显著提升了用户体验，使任务管理更加直观和便捷。
