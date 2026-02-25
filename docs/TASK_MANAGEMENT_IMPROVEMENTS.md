# 任务管理功能优化说明

## 当前问题

web-modern的任务管理功能与旧版web相比缺少以下功能：

1. **任务名称字段缺失** - 创建任务时没有任务名称输入
2. **执行次数显示** - 没有显示任务执行了多少次
3. **任务历史记录** - 无法查看任务的历史执行记录
4. **日志自动刷新** - 缺少日志自动刷新功能
5. **批量任务详情** - 批量任务详情展示不够完善
6. **任务重启功能** - 失败任务无法重启

## 需要添加的功能

### 1. 任务列表改进

**添加字段：**
- 任务名称（taskName）- 显示在任务ID上方
- 执行次数（executionCount）- 显示"第X次"标签
- 完成时间（finishedAt）

**表格列调整：**
```
| 任务信息 | Agent | 执行次数 | 脚本类型 | 状态 | 创建者 | 创建时间 | 完成时间 | 操作 |
```

### 2. 创建任务对话框

**添加字段：**
```jsx
<el-form-item label="任务名称" required>
  <el-input 
    v-model="createTaskForm.taskName" 
    placeholder="请输入任务名称（必填）"
    maxlength="100"
    show-word-limit>
  </el-input>
</el-form-item>
```

### 3. 任务操作按钮

**根据状态显示不同按钮：**
- 详情 - 所有任务
- 日志 - executionCount > 0的任务
- 重启 - FAILED或TIMEOUT状态的任务
- 历史 - executionCount > 1的任务
- 取消 - PENDING或RUNNING状态的任务

### 4. 任务历史记录功能

**添加历史记录对话框：**
```jsx
<el-dialog v-model="showHistoryDialog" title="任务执行历史" width="900px">
  <el-table :data="taskExecutions">
    <el-table-column prop="executionId" label="执行ID" width="100" />
    <el-table-column prop="executionNumber" label="执行次数" width="100" />
    <el-table-column prop="status" label="状态" width="100" />
    <el-table-column prop="startedAt" label="开始时间" width="160" />
    <el-table-column prop="finishedAt" label="完成时间" width="160" />
    <el-table-column prop="exitCode" label="退出码" width="80" />
    <el-table-column label="操作" width="150">
      <template #default="scope">
        <el-button size="small" @click="viewExecutionLogs(scope.row)">
          查看日志
        </el-button>
      </template>
    </el-table-column>
  </el-table>
</el-dialog>
```

### 5. 日志查看改进

**添加功能：**
- 自动刷新开关
- 下载日志按钮
- 清空显示按钮
- 日志行数显示

**日志工具栏：**
```jsx
<div class="logs-toolbar">
  <el-button size="small" @click="refreshLogs">刷新日志</el-button>
  <el-button size="small" @click="clearLogs">清空显示</el-button>
  <el-button size="small" type="success" @click="downloadTaskLog">
    下载日志
  </el-button>
  <el-switch v-model="autoRefreshLogs" active-text="自动刷新" />
  <span style="margin-left: 10px;">
    共 {{ taskLogTotalLines }} 行
  </span>
</div>
```

### 6. 批量任务详情改进

**添加统计卡片：**
```jsx
<el-card>
  <template #header>
    <div style="display: flex; justify-content: space-between;">
      <span>执行进度</span>
      <el-tag :type="getBatchStatusType(selectedBatchTask.status)">
        {{ getBatchStatusText(selectedBatchTask.status) }}
      </el-tag>
    </div>
  </template>
  <el-progress 
    :percentage="Math.round(selectedBatchTask.progress || 0)" 
    :stroke-width="20">
  </el-progress>
  <div style="margin-top: 15px; display: flex; justify-content: space-around;">
    <div style="text-align: center;">
      <div style="font-size: 24px; font-weight: bold; color: #67C23A;">
        {{ selectedBatchTask.successTasks }}
      </div>
      <div style="color: #999;">成功</div>
    </div>
    <!-- 其他统计... -->
  </div>
</el-card>
```

## API接口

### 需要的后端接口：

1. **获取任务历史** - `GET /api/web/tasks/{taskId}/executions`
2. **获取历史日志** - `GET /api/web/tasks/executions/{executionId}/logs`
3. **下载日志** - `GET /api/web/tasks/{taskId}/logs/download`
4. **重启任务** - `POST /api/web/tasks/{taskId}/restart`
5. **刷新批量任务统计** - `POST /api/web/batch-tasks/{batchId}/refresh-stats`

## 实施步骤

1. ✅ 修改后端API，添加taskName字段支持
2. ✅ 修改前端创建任务表单，添加taskName输入
3. ✅ 修改任务列表显示，添加执行次数和完成时间
4. ✅ 添加任务历史记录功能
5. ✅ 优化日志查看功能
6. ✅ 改进批量任务详情展示
7. ✅ 添加任务重启功能

## 参考文件

- 旧版HTML: `web/tasks.html`
- 旧版JS: `web/js/tasks.js`
- 新版React: `web-modern/src/pages/Tasks.jsx`

## 注意事项

1. 保持React组件的现代化设计风格
2. 使用Ant Design 5的组件
3. 确保API调用使用正确的路径（/api/web/...）
4. 添加适当的加载状态和错误处理
5. 保持代码简洁，避免过度复杂

## 预期效果

优化后的任务管理功能应该：
- 更直观地显示任务信息
- 支持查看任务执行历史
- 提供更好的日志查看体验
- 批量任务详情更加清晰
- 操作按钮根据任务状态智能显示
