# Bug修复：任务状态筛选功能

## 修复日期
2026-02-28

## 问题描述

用户报告任务列表页面上的状态筛选器没有生效，选择不同的状态后，任务列表没有相应的变化。

## 问题原因

前端和后端都缺少状态筛选的实现：

1. **前端问题**：
   - Select组件使用了`defaultValue`而不是`value`，没有状态绑定
   - 没有`onChange`事件处理函数
   - 没有状态变量来存储选中的筛选值
   - fetchTasks函数没有传递状态参数

2. **后端问题**：
   - WebController的getAllTasks接口没有接收status参数
   - TaskService没有按状态查询的方法
   - TaskRepository没有按状态分页查询的方法

## 修复方案

### 前端修复

#### 1. 添加状态变量
```javascript
const [statusFilter, setStatusFilter] = useState('all')
```

#### 2. 修改fetchTasks函数
```javascript
const fetchTasks = async () => {
  setLoading(true)
  try {
    const params = {
      page: currentPage - 1,
      size: pageSize
    }
    
    // 添加状态筛选参数
    if (statusFilter && statusFilter !== 'all') {
      params.status = statusFilter
    }
    
    const response = await api.get('/web/tasks', { params })
    // ...
  }
}
```

#### 3. 修改useEffect依赖项
```javascript
useEffect(() => {
  fetchTasks()
  fetchOnlineAgents()
}, [currentPage, pageSize, statusFilter])  // 添加statusFilter依赖
```

#### 4. 修改Select组件
```javascript
<Select
  value={statusFilter}
  onChange={(value) => {
    setStatusFilter(value)
    setCurrentPage(1) // 重置到第一页
  }}
  style={{ width: 150 }}
>
  <Option value="all">全部状态</Option>
  <Option value="DRAFT">草稿</Option>
  <Option value="PENDING">待执行</Option>
  <Option value="RUNNING">执行中</Option>
  <Option value="SUCCESS">成功</Option>
  <Option value="FAILED">失败</Option>
  <Option value="PARTIAL_SUCCESS">部分成功</Option>
  <Option value="STOPPED">已停止</Option>
  <Option value="CANCELLED">已取消</Option>
</Select>
```

### 后端修复

#### 1. 修改WebController
```java
@GetMapping("/tasks")
public ResponseEntity<Page<TaskModels.TaskDTO>> getAllTasks(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<TaskModels.TaskDTO> tasks;
    
    if (status != null && !status.isEmpty()) {
        tasks = taskService.getTasksByStatus(status, pageable);
    } else {
        tasks = taskService.getAllTasksWithStatus(pageable);
    }
    
    return ResponseEntity.ok(tasks);
}
```

#### 2. 添加TaskService方法
```java
/**
 * 按状态获取任务（包含聚合状态）
 */
public Page<TaskModels.TaskDTO> getTasksByStatus(String status, Pageable pageable) {
    Page<Task> taskPage = taskRepository.findByTaskStatus(status, pageable);
    
    List<TaskModels.TaskDTO> taskDTOs = taskPage.getContent().stream()
            .map(task -> {
                List<TaskExecution> executions = taskExecutionService.getExecutionsByTaskId(task.getTaskId());
                return toTaskDTO(task, executions);
            })
            .collect(Collectors.toList());
    
    return new PageImpl<>(taskDTOs, pageable, taskPage.getTotalElements());
}
```

#### 3. 添加TaskRepository方法
```java
// 按状态分页查询
Page<Task> findByTaskStatus(String taskStatus, Pageable pageable);
```

## 测试验证

### 后端API测试

#### 测试1：查询所有任务
```bash
curl "http://localhost:8080/api/web/tasks?page=0&size=10"
# 返回所有任务
```

#### 测试2：查询草稿状态任务
```bash
curl "http://localhost:8080/api/web/tasks?page=0&size=10&status=DRAFT"
# 结果：
# Total: 1
# draft-task-test: DRAFT
```

#### 测试3：查询成功状态任务
```bash
curl "http://localhost:8080/api/web/tasks?page=0&size=10&status=SUCCESS"
# 结果：
# Total: 3
# test-sort-order: SUCCESS
# feature-1: SUCCESS
# feature-test-draft: SUCCESS
```

✅ 后端API测试通过

### 前端测试

1. 访问 http://localhost:3001 任务列表页面
2. 点击状态筛选下拉框
3. 选择"草稿"状态
4. 验证：列表只显示草稿状态的任务
5. 选择"成功"状态
6. 验证：列表只显示成功状态的任务
7. 选择"全部状态"
8. 验证：列表显示所有任务

✅ 前端功能测试通过

## 文件变更清单

### 前端文件
1. `web-modern/src/pages/Tasks.jsx`
   - 添加了`statusFilter`状态变量
   - 修改了`fetchTasks`函数，添加状态参数
   - 修改了`useEffect`依赖项
   - 修改了Select组件，添加value绑定和onChange事件

### 后端文件
1. `server/src/main/java/com/example/lightscript/server/web/WebController.java`
   - 修改了`getAllTasks`方法，添加status参数
   - 根据status参数调用不同的Service方法

2. `server/src/main/java/com/example/lightscript/server/service/TaskService.java`
   - 添加了`getTasksByStatus`方法

3. `server/src/main/java/com/example/lightscript/server/repository/TaskRepository.java`
   - 添加了`findByTaskStatus`方法

## 用户体验改进

### 修复前
- 状态筛选器是装饰性的，没有实际功能
- 用户无法按状态筛选任务
- 需要手动浏览所有任务来找到特定状态的任务

### 修复后
- 状态筛选器完全可用
- 用户可以快速筛选特定状态的任务
- 提升了任务管理效率
- 改善了用户体验

## 技术细节

### 前端状态管理
- 使用React的useState管理筛选状态
- 使用useEffect监听状态变化并自动刷新数据
- 切换筛选条件时自动重置到第一页

### 后端查询优化
- 使用Spring Data JPA的方法命名查询
- 支持分页和排序
- 查询在数据库层面执行，性能优秀

### API设计
- status参数为可选参数（required = false）
- 不传递status参数时返回所有任务
- 传递status参数时返回指定状态的任务
- 保持向后兼容性

## 部署说明

### 后端部署
1. 重新编译：`mvn clean package -DskipTests`
2. 重启服务：`java -jar server/target/server-0.1.0-SNAPSHOT.jar`
3. 无需数据库迁移

### 前端部署
- Vite开发模式下自动热更新
- 生产环境需要重新构建：`npm run build`

## 总结

任务状态筛选功能已完全修复，前后端都实现了完整的筛选逻辑。用户现在可以方便地按状态筛选任务，大大提升了任务管理的效率。这是一个重要的用户体验改进。
