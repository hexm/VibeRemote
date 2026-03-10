# 文件传输任务集成设计

**日期**: 2026-03-10  
**功能**: 文件传输与任务系统集成  
**状态**: 设计阶段

## 需求更新

基于用户反馈，文件传输功能需要与现有任务系统深度集成：

1. **任务类型扩展**: 新增文件传输任务类型
2. **多客户端传输**: 一个任务可以向多个客户端传输同一文件
3. **状态汇总**: 各客户端传输状态汇总为任务整体状态
4. **详细视图**: 任务详情中显示每个文件传输项的状态

## 系统架构设计

### 1. 任务类型扩展

```java
// Task实体扩展
@Entity
@Table(name = "tasks")
public class Task {
    // 现有字段...
    
    @Column(name = "task_type", length = 20)
    private String taskType = "SCRIPT"; // SCRIPT | FILE_TRANSFER
    
    // 对于文件传输任务，scriptContent字段存储文件传输配置JSON
    // 对于脚本任务，保持原有逻辑
}
```

### 2. 文件传输配置

```java
// 文件传输任务配置（存储在Task.scriptContent中的JSON）
public class FileTransferConfig {
    private String sourceFileId;      // 源文件ID
    private String targetPath;        // 目标路径
    private String transferMode;      // 传输模式：OVERWRITE, BACKUP, VERSION
    private Boolean verifyChecksum;   // 是否校验文件完整性
    private Map<String, String> env;  // 环境变量（可选）
}
```

### 3. 文件传输执行结果

```java
// 扩展TaskExecution实体
@Entity
@Table(name = "task_executions")
public class TaskExecution {
    // 现有字段...
    
    // 文件传输相关字段
    @Column(name = "file_id", length = 50)
    private String fileId; // 传输的文件ID
    
    @Column(name = "target_path", length = 500)
    private String targetPath; // 目标路径
    
    @Column(name = "transfer_size")
    private Long transferSize; // 实际传输大小
    
    @Column(name = "checksum_verified")
    private Boolean checksumVerified; // 校验是否通过
    
    @Column(name = "transfer_speed")
    private Long transferSpeed; // 传输速度（字节/秒）
    
    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails; // 详细错误信息
}
```

## 数据库设计

### 1. 文件管理表

```sql
-- 文件管理表
CREATE TABLE files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_id VARCHAR(50) NOT NULL UNIQUE COMMENT '文件ID，如F001',
    name VARCHAR(255) NOT NULL COMMENT '文件名',
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_path VARCHAR(500) NOT NULL COMMENT '服务器存储路径',
    file_size BIGINT NOT NULL COMMENT '文件大小（字节）',
    file_type VARCHAR(100) COMMENT '文件类型/MIME类型',
    category VARCHAR(50) COMMENT '文件分类',
    version VARCHAR(20) DEFAULT '1.0' COMMENT '文件版本',
    md5 VARCHAR(32) COMMENT 'MD5校验和',
    sha256 VARCHAR(64) COMMENT 'SHA256校验和',
    description TEXT COMMENT '文件描述',
    tags VARCHAR(500) COMMENT '标签，逗号分隔',
    upload_by VARCHAR(100) NOT NULL COMMENT '上传者',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_file_id (file_id),
    INDEX idx_category (category),
    INDEX idx_upload_by (upload_by),
    INDEX idx_md5 (md5),
    INDEX idx_sha256 (sha256)
);
```

**说明**:
- `md5`: 存储文件的MD5校验和（32位十六进制字符串）
- `sha256`: 存储文件的SHA256校验和（64位十六进制字符串）
- 两个校验和都会在文件上传时自动计算并存储
- 添加索引以支持基于校验和的快速查询（用于文件去重等场景）

### 2. 任务表扩展

```sql
-- 扩展任务表
ALTER TABLE tasks ADD COLUMN task_type VARCHAR(20) DEFAULT 'SCRIPT' COMMENT '任务类型：SCRIPT, FILE_TRANSFER';

-- 为文件传输任务，scriptContent字段存储JSON配置：
-- {
--   "sourceFileId": "F001",
--   "targetPath": "/opt/app/config.json",
--   "transferMode": "OVERWRITE",
--   "verifyChecksum": true
-- }
```

### 3. 任务执行表扩展

```sql
-- 扩展任务执行表
ALTER TABLE task_executions ADD COLUMN file_id VARCHAR(50) COMMENT '传输的文件ID';
ALTER TABLE task_executions ADD COLUMN target_path VARCHAR(500) COMMENT '目标路径';
ALTER TABLE task_executions ADD COLUMN transfer_size BIGINT COMMENT '实际传输大小';
ALTER TABLE task_executions ADD COLUMN checksum_verified BOOLEAN COMMENT '校验是否通过';
ALTER TABLE task_executions ADD COLUMN transfer_speed BIGINT COMMENT '传输速度（字节/秒）';
ALTER TABLE task_executions ADD COLUMN error_details TEXT COMMENT '详细错误信息';

-- 添加索引
CREATE INDEX idx_task_executions_file_id ON task_executions(file_id);
```

## API设计

### 1. 文件管理API

```java
@RestController
@RequestMapping("/api/web/files")
public class FileController {
    
    @PostMapping("/upload")
    @RequirePermission("file:upload")
    public FileDTO uploadFile(@RequestParam("file") MultipartFile file,
                             @RequestParam("category") String category,
                             @RequestParam(value = "description", required = false) String description);
    
    @GetMapping
    @RequirePermission("file:list")
    public Page<FileDTO> getFiles(@RequestParam(required = false) String category,
                                 @RequestParam(required = false) String keyword,
                                 @RequestParam(defaultValue = "0") Integer page,
                                 @RequestParam(defaultValue = "10") Integer size);
    
    @GetMapping("/{fileId}")
    @RequirePermission("file:view")
    public FileDTO getFileInfo(@PathVariable String fileId);
    
    @GetMapping("/{fileId}/download")
    @RequirePermission("file:download")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileId);
    
    @DeleteMapping("/{fileId}")
    @RequirePermission("file:delete")
    public ResponseEntity<Void> deleteFile(@PathVariable String fileId);
    
    // 用于任务创建的文件列表
    @GetMapping("/for-task")
    @RequirePermission("file:list")
    public List<FileForTaskDTO> getFilesForTask();
}
```

### 2. 任务API扩展

```java
// 扩展TaskController
@PostMapping("/create-file-transfer")
@RequirePermission("task:create")
public CreateTaskResponse createFileTransferTask(@RequestBody CreateFileTransferTaskRequest request);

// Agent API扩展
@GetMapping("/files/{fileId}/download")
public ResponseEntity<Resource> downloadFileForAgent(@PathVariable String fileId,
                                                    @RequestHeader("Agent-ID") String agentId);

@PostMapping("/file-transfer/result")
public ResponseEntity<Void> reportTransferResult(@RequestBody FileTransferResultRequest request);
```

### 3. 数据模型

```java
// 文件传输任务创建请求
@Data
public static class CreateFileTransferTaskRequest {
    @NotEmpty(message = "至少需要选择一个代理")
    private List<String> agentIds;
    
    @NotBlank(message = "任务名称不能为空")
    private String taskName;
    
    @NotBlank(message = "源文件ID不能为空")
    private String sourceFileId;
    
    @NotBlank(message = "目标路径不能为空")
    private String targetPath;
    
    @NotBlank(message = "传输模式不能为空")
    private String transferMode; // OVERWRITE, BACKUP, VERSION
    
    private Boolean verifyChecksum = true;
    private Integer timeoutSec = 300;
}

// 文件传输结果报告
@Data
public static class FileTransferResultRequest {
    @NotNull(message = "执行ID不能为空")
    private Long executionId;
    
    @NotBlank(message = "传输状态不能为空")
    private String status; // SUCCESS, FAILED, PARTIAL
    
    private Long transferSize;
    private Boolean checksumVerified;
    private Long transferSpeed;
    private String errorDetails;
}

// 文件DTO
@Data
public static class FileDTO {
    private String fileId;
    private String name;
    private String originalName;
    private String filePath;
    private Long fileSize;
    private String sizeDisplay;
    private String fileType;
    private String category;
    private String version;
    private String checksum;
    private String description;
    private String tags;
    private String uploadBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// 用于任务创建的文件DTO
@Data
public static class FileForTaskDTO {
    private String fileId;
    private String name;
    private String originalName;
    private Long fileSize;
    private String sizeDisplay;
    private String category;
    private String checksum;
}
```

## 前端设计

### 1. 导航菜单扩展

```javascript
// 添加文件管理菜单
{
  key: 'files',
  icon: <FileOutlined />,
  label: '文件管理',
  path: '/files'
}
```

### 2. 任务创建页面扩展

```javascript
// 任务类型选择
<Form.Item label="任务类型">
  <Radio.Group value={taskType} onChange={(e) => setTaskType(e.target.value)}>
    <Radio value="SCRIPT">脚本执行</Radio>
    <Radio value="FILE_TRANSFER">文件传输</Radio>
  </Radio.Group>
</Form.Item>

// 文件传输配置
{taskType === 'FILE_TRANSFER' && (
  <div className="space-y-4">
    <Form.Item
      name="sourceFileId"
      label="源文件"
      rules={[{ required: true, message: '请选择要传输的文件' }]}
    >
      <Select placeholder="选择要传输的文件">
        {availableFiles.map(file => (
          <Option key={file.fileId} value={file.fileId}>
            <Space>
              <FileOutlined />
              {file.name}
              <Tag color="blue">{file.sizeDisplay}</Tag>
            </Space>
          </Option>
        ))}
      </Select>
    </Form.Item>
    
    <Form.Item
      name="targetPath"
      label="目标路径"
      rules={[{ required: true, message: '请输入目标路径' }]}
    >
      <Input placeholder="例如：/opt/app/config.json" />
    </Form.Item>
    
    <Form.Item
      name="transferMode"
      label="传输模式"
      initialValue="OVERWRITE"
    >
      <Radio.Group>
        <Radio value="OVERWRITE">直接覆盖</Radio>
        <Radio value="BACKUP">备份后覆盖</Radio>
        <Radio value="VERSION">版本管理</Radio>
      </Radio.Group>
    </Form.Item>
    
    <Form.Item
      name="verifyChecksum"
      label="文件校验"
      valuePropName="checked"
      initialValue={true}
    >
      <Switch checkedChildren="启用" unCheckedChildren="禁用" />
    </Form.Item>
  </div>
)}
```

### 3. 任务详情页面扩展

```javascript
// 文件传输任务的执行实例列表
const fileTransferColumns = [
  {
    title: '执行ID',
    dataIndex: 'id',
    key: 'id',
    width: 80,
    render: (id) => <Text code>{id}</Text>
  },
  {
    title: 'Agent',
    dataIndex: 'agentId',
    key: 'agentId',
    width: 150,
    render: (agentId) => (
      <Tag color="blue" size="small">
        {getAgentName(agentId)}
      </Tag>
    )
  },
  {
    title: '文件信息',
    key: 'fileInfo',
    width: 200,
    render: (_, record) => (
      <div>
        <Text strong>{record.fileName}</Text>
        <div>
          <Text type="secondary" className="text-xs">
            {record.fileSizeDisplay}
          </Text>
        </div>
      </div>
    )
  },
  {
    title: '目标路径',
    dataIndex: 'targetPath',
    key: 'targetPath',
    width: 200,
    render: (path) => <Text code className="text-xs">{path}</Text>
  },
  {
    title: '传输状态',
    dataIndex: 'status',
    key: 'status',
    width: 100,
    render: (status) => (
      <Tag color={getExecutionStatusColor(status)} size="small">
        {getExecutionStatusText(status)}
      </Tag>
    )
  },
  {
    title: '传输进度',
    key: 'progress',
    width: 150,
    render: (_, record) => {
      if (record.status === 'RUNNING' && record.transferSize && record.fileSize) {
        const percent = Math.round((record.transferSize / record.fileSize) * 100)
        return <Progress percent={percent} size="small" />
      }
      return record.status === 'SUCCESS' ? 
        <Progress percent={100} size="small" status="success" /> : 
        <Text type="secondary">-</Text>
    }
  },
  {
    title: '传输速度',
    dataIndex: 'transferSpeed',
    key: 'transferSpeed',
    width: 120,
    render: (speed) => speed ? formatSpeed(speed) : '-'
  },
  {
    title: '校验结果',
    dataIndex: 'checksumVerified',
    key: 'checksumVerified',
    width: 100,
    render: (verified) => {
      if (verified === null || verified === undefined) return '-'
      return verified ? 
        <Tag color="success" size="small">通过</Tag> : 
        <Tag color="error" size="small">失败</Tag>
    }
  },
  {
    title: '开始时间',
    dataIndex: 'startedAt',
    key: 'startedAt',
    width: 160,
    render: (time) => <Text className="text-xs">{formatDateTime(time)}</Text>
  },
  {
    title: '完成时间',
    dataIndex: 'finishedAt',
    key: 'finishedAt',
    width: 160,
    render: (time) => <Text className="text-xs">{formatDateTime(time)}</Text>
  },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    render: (_, record) => (
      <Space size="small">
        {record.errorDetails && (
          <Tooltip title={record.errorDetails}>
            <Button 
              type="link" 
              size="small"
              icon={<ExclamationCircleOutlined />}
              danger
            >
              错误
            </Button>
          </Tooltip>
        )}
        {(record.status === 'PENDING' || record.status === 'RUNNING') && (
          <Button 
            type="link" 
            size="small"
            icon={<StopOutlined />}
            danger
            onClick={() => handleCancelExecution(record)}
          >
            取消
          </Button>
        )}
      </Space>
    )
  }
]
```

## 执行流程设计

### 1. 文件传输任务创建流程

```
1. 用户选择任务类型为"文件传输"
2. 选择源文件（从文件管理中选择）
3. 配置目标路径和传输参数
4. 选择目标Agent列表
5. 创建任务记录（task_type = 'FILE_TRANSFER'）
6. 为每个Agent创建执行实例
7. 如果自动启动，开始执行
```

### 2. Agent执行流程

```
1. Agent拉取文件传输任务
2. 解析任务配置（从scriptContent JSON中）
3. 向服务器请求下载文件
4. 验证下载权限和Agent身份
5. 开始文件传输，报告进度
6. 完成后验证文件完整性
7. 报告传输结果
```

### 3. 状态汇总逻辑

```java
// 任务状态计算逻辑
public String calculateTaskStatus(List<TaskExecution> executions) {
    if (executions.isEmpty()) return "PENDING";
    
    long total = executions.size();
    long success = executions.stream().filter(e -> "SUCCESS".equals(e.getStatus())).count();
    long failed = executions.stream().filter(e -> "FAILED".equals(e.getStatus())).count();
    long running = executions.stream().filter(e -> 
        Arrays.asList("PENDING", "PULLED", "RUNNING").contains(e.getStatus())).count();
    
    if (running > 0) return "RUNNING";
    if (success == total) return "SUCCESS";
    if (failed == total) return "FAILED";
    if (success > 0) return "PARTIAL_SUCCESS";
    return "FAILED";
}
```

## 安全考虑

### 1. 文件访问控制
- Agent下载文件需要验证身份和权限
- 使用临时下载令牌，限制有效期
- 记录所有文件访问日志

### 2. 路径安全
- 目标路径白名单验证
- 防止路径遍历攻击
- Agent端路径权限检查

### 3. 传输安全
- 文件完整性校验（SHA256）
- 传输过程加密（HTTPS）
- 失败重试机制

## 实施计划

### 第一阶段：基础功能
1. 文件管理模块（上传、列表、下载、删除）
2. 任务类型扩展（支持文件传输任务）
3. 基础传输功能和状态报告

### 第二阶段：增强功能
1. 传输进度显示
2. 断点续传
3. 批量文件传输

### 第三阶段：高级功能
1. 文件版本管理
2. 增量同步
3. 性能监控和优化

## 总结

这个设计将文件传输功能完全集成到现有的任务系统中，实现了：

1. **统一的任务管理**: 文件传输任务与脚本任务使用相同的管理界面
2. **详细的状态跟踪**: 每个Agent的传输状态都被记录和显示
3. **灵活的配置**: 支持多种传输模式和参数配置
4. **完整的监控**: 传输进度、速度、错误信息等全面监控
5. **安全的传输**: 身份验证、权限控制、完整性校验

这样的设计既满足了用户的需求，又保持了系统架构的一致性和可扩展性。