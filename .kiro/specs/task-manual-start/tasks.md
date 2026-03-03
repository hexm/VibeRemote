# 任务手动启动功能 - 实施任务列表

## 1. 数据库变更
- [ ] 1.1 创建数据库迁移脚本 V5__task_manual_start_support.sql
  - 添加task_status字段（VARCHAR(20)，默认'PENDING'）
  - 添加task_status索引
  - 更新现有数据的task_status（根据执行实例状态计算）

## 2. 实体层修改
- [ ] 2.1 Task实体增加taskStatus字段
  - 添加@Column注解，字段名task_status
  - 添加getter/setter方法

## 3. Repository层修改
- [ ] 3.1 TaskRepository增加按状态查询方法
  - findByTaskStatus(String taskStatus): List<Task>
  - countByTaskStatus(String taskStatus): long
  
- [ ] 3.2 TaskExecutionRepository增加查询可执行任务的执行实例方法
  - findPendingExecutionsForActiveTasks(String agentId): List<TaskExecution>
  - 使用@Query注解，JOIN Task表，排除DRAFT/STOPPED/CANCELLED状态

## 4. DTO和Model
- [ ] 4.1 在TaskModels中创建StartTaskResponse类
  - taskId: String
  - taskStatus: String
  - executionCount: Integer
  - message: String
  
- [ ] 4.2 在TaskModels中创建StopTaskResponse类
  - taskId: String
  - taskStatus: String
  - cancelledCount: Integer
  - message: String
  
- [ ] 4.3 修改CreateTaskResponse包含taskStatus字段
  
- [ ] 4.4 修改TaskDTO包含taskStatus字段

## 5. Service层实现 - 核心状态管理
- [ ] 5.1 TaskService增加calculateTaskStatus方法
  - 参数：String taskId
  - 返回：String（任务状态）
  - 逻辑：
    * 如果任务状态是DRAFT，直接返回DRAFT
    * 获取所有执行实例，统计各状态数量
    * 根据设计文档的规则计算状态：
      - 有RUNNING或PULLED → RUNNING
      - 全部SUCCESS → SUCCESS
      - 全部FAILED → FAILED
      - 全部CANCELLED → CANCELLED
      - 部分SUCCESS部分FAILED → PARTIAL_SUCCESS
      - 全部PENDING → PENDING
  
- [ ] 5.2 TaskService增加updateTaskStatus方法
  - 参数：String taskId
  - 逻辑：
    * 调用calculateTaskStatus计算新状态
    * 更新Task实体的taskStatus字段
    * 保存到数据库
    * 记录日志（状态变更）

## 6. Service层实现 - 任务启动功能
- [ ] 6.1 TaskService增加startTask方法
  - 参数：String taskId
  - 返回：StartTaskResponse
  - 逻辑：
    * 查询任务，验证存在
    * 验证任务状态为DRAFT，否则抛出异常
    * 获取任务的目标代理列表（从哪里获取？需要在Task表增加字段或从其他地方获取）
    * 为所有目标代理创建执行实例（调用taskExecutionService.createExecutions）
    * 调用updateTaskStatus更新状态
    * 返回StartTaskResponse

## 7. Service层实现 - 任务停止功能
- [ ] 7.1 TaskService增加stopTask方法
  - 参数：String taskId
  - 返回：StopTaskResponse
  - 逻辑：
    * 查询任务，验证存在
    * 计算当前状态，验证可停止（PENDING/RUNNING）
    * 查询所有未完成的执行实例（PENDING/PULLED/RUNNING）
    * 取消这些执行实例（调用taskExecutionService.cancelExecution）
    * 调用updateTaskStatus更新状态
    * 返回StopTaskResponse（包含取消的执行实例数量）

## 8. Service层实现 - 修改现有方法
- [ ] 8.1 修改createMultiAgentTask方法
  - 增加boolean autoStart参数（默认true）
  - 如果autoStart=false：
    * 设置task.taskStatus = "DRAFT"
    * 不创建执行实例
  - 如果autoStart=true：
    * 设置task.taskStatus = "PENDING"
    * 创建执行实例（保持现有逻辑）
    * 调用updateTaskStatus更新状态
  - 响应中包含taskStatus字段
  
- [ ] 8.2 修改pullTasks方法
  - 使用新的Repository方法findPendingExecutionsForActiveTasks
  - 只查询非DRAFT、非STOPPED、非CANCELLED状态任务的PENDING执行实例
  
- [ ] 8.3 修改restartTask方法
  - 验证任务状态可重启（SUCCESS/FAILED/PARTIAL_SUCCESS/STOPPED/CANCELLED）
  - 重启后调用updateTaskStatus更新状态
  
- [ ] 8.4 修改ackTaskExecution方法
  - 执行完成后调用updateTaskStatus(execution.getTaskId())
  
- [ ] 8.5 修改finishTask方法
  - 执行完成后调用updateTaskStatus(execution.getTaskId())
  
- [ ] 8.6 修改cancelExecution方法（在TaskExecutionService中）
  - 取消后调用taskService.updateTaskStatus(execution.getTaskId())

## 9. Controller层实现
- [ ] 9.1 WebController增加startTask接口
  - 路径：POST /api/web/tasks/{taskId}/start
  - 参数：@PathVariable String taskId
  - 调用：taskService.startTask(taskId)
  - 返回：ResponseEntity<StartTaskResponse>
  - 异常处理：任务不存在（404）、状态不允许（400）
  
- [ ] 9.2 WebController增加stopTask接口
  - 路径：POST /api/web/tasks/{taskId}/stop
  - 参数：@PathVariable String taskId
  - 调用：taskService.stopTask(taskId)
  - 返回：ResponseEntity<StopTaskResponse>
  - 异常处理：任务不存在（404）、状态不允许（400）
  
- [ ] 9.3 修改createTask接口
  - 增加@RequestParam(defaultValue = "true") Boolean autoStart参数
  - 传递autoStart到taskService.createMultiAgentTask
  - 响应中包含taskStatus字段

## 10. 前端实现 - 创建任务界面
- [ ] 10.1 Tasks.jsx增加"立即启动"复选框
  - 位置：创建任务表单中
  - 默认值：true（勾选）
  - 提示文字："取消勾选后，任务将保存为草稿，需要手动启动"
  
- [ ] 10.2 修改创建任务API调用
  - 在请求参数中增加autoStart字段
  - 根据复选框状态设置值

## 11. 前端实现 - 任务列表界面
- [ ] 11.1 Tasks.jsx显示任务状态标签
  - 在任务列表中显示taskStatus字段
  - 使用不同颜色标识不同状态：
    * DRAFT: 灰色
    * PENDING: 蓝色
    * RUNNING: 黄色
    * SUCCESS: 绿色
    * FAILED: 红色
    * PARTIAL_SUCCESS: 橙色
    * STOPPED: 灰色
    * CANCELLED: 灰色
  
- [ ] 11.2 Tasks.jsx增加状态筛选器
  - 位置：任务列表顶部
  - 选项：全部、草稿、待执行、执行中、成功、失败、部分成功、已停止、已取消
  - 筛选逻辑：根据选择的状态过滤任务列表
  
- [ ] 11.3 Tasks.jsx根据状态显示操作按钮
  - DRAFT状态：显示"启动"按钮
  - PENDING/RUNNING状态：显示"停止"按钮
  - SUCCESS/FAILED/PARTIAL_SUCCESS/STOPPED/CANCELLED状态：显示"重启"按钮

## 12. 前端实现 - API调用
- [ ] 12.1 实现startTask API调用
  - 方法：POST /api/web/tasks/{taskId}/start
  - 成功后刷新任务列表
  - 显示成功/失败提示
  
- [ ] 12.2 实现stopTask API调用
  - 方法：POST /api/web/tasks/{taskId}/stop
  - 成功后刷新任务列表
  - 显示成功/失败提示

## 13. 测试 - 单元测试
- [ ] 13.1 TaskServiceTest.testCalculateTaskStatus
  - 测试场景：
    * DRAFT状态任务
    * 全部PENDING
    * 全部SUCCESS
    * 全部FAILED
    * 部分SUCCESS部分FAILED
    * 有RUNNING
    * 全部CANCELLED
  
- [ ] 13.2 TaskServiceTest.testStartTask
  - 测试场景：
    * 正常启动DRAFT任务
    * 启动非DRAFT任务（应抛出异常）
    * 任务不存在（应抛出异常）
  
- [ ] 13.3 TaskServiceTest.testStopTask
  - 测试场景：
    * 正常停止PENDING任务
    * 正常停止RUNNING任务
    * 停止DRAFT任务（应抛出异常）
    * 停止已完成任务（应抛出异常）

## 14. 测试 - 集成测试
- [ ] 14.1 创建草稿任务集成测试
  - 创建任务（autoStart=false）
  - 验证任务状态为DRAFT
  - 验证没有创建执行实例
  - 验证Agent无法拉取该任务
  
- [ ] 14.2 启动任务集成测试
  - 创建草稿任务
  - 调用启动API
  - 验证任务状态变为PENDING
  - 验证创建了执行实例
  - 验证Agent可以拉取任务
  
- [ ] 14.3 停止任务集成测试
  - 创建并启动任务
  - 调用停止API
  - 验证任务状态变为STOPPED或CANCELLED
  - 验证执行实例被取消
  
- [ ] 14.4 状态自动更新集成测试
  - 创建并启动任务
  - Agent拉取任务（状态变为PULLED）
  - Agent确认任务（状态变为RUNNING）
  - 验证任务状态自动变为RUNNING
  - Agent完成任务（状态变为SUCCESS）
  - 验证任务状态自动变为SUCCESS

## 15. 测试 - 端到端测试
- [ ] 15.1 完整流程测试
  - 前端创建草稿任务
  - 前端启动任务
  - Agent拉取并执行任务
  - 验证任务状态变化：DRAFT -> PENDING -> RUNNING -> SUCCESS
  
- [ ] 15.2 停止流程测试
  - 前端创建并启动任务
  - 前端停止任务
  - 验证任务状态变为STOPPED
  - 验证Agent无法拉取该任务
  
- [ ] 15.3 重启流程测试
  - 创建并完成任务
  - 前端重启任务
  - 验证创建了新的执行实例
  - 验证任务状态变为PENDING

## 16. 文档和部署
- [ ] 16.1 更新API文档
  - 记录新增的启动/停止接口
  - 记录修改的创建任务接口（autoStart参数）
  
- [ ] 16.2 创建功能说明文档
  - 说明任务状态的含义
  - 说明如何使用手动启动功能
  
- [ ] 16.3 部署到测试环境
  - 执行数据库迁移
  - 部署后端代码
  - 部署前端代码
  - 验证功能正常
  
- [ ] 16.4 部署到生产环境（阿里云）
  - 备份数据库
  - 执行数据库迁移
  - 部署后端代码
  - 部署前端代码
  - 验证功能正常
