# 文件传输功能需求分析

**日期**: 2026-03-10  
**功能**: 文件管理和文件传输任务  
**状态**: 需求分析阶段

## 原始需求

### 用户需求
1. **文件管理菜单** - 支持上传文件到服务器
2. **文件传输任务** - 创建任务时支持文件传输类型
3. **任务要素**:
   - 源文件：从文件管理中引用
   - 目标文件：带文件名称
4. **执行机制** - 客户端从服务器下载文件并报告结果

## 需求分析与优化建议

### 1. 功能架构设计

#### 1.1 文件管理模块
```
文件管理 (Files)
├── 文件上传
├── 文件列表
├── 文件预览
├── 文件下载
├── 文件删除
└── 文件分类/标签
```

**建议优化**:
- **文件分类**: 按用途分类（配置文件、安装包、文档等）
- **版本管理**: 支持同一文件的多个版本
- **文件校验**: MD5/SHA256校验确保文件完整性
- **存储限制**: 设置文件大小和总存储空间限制

#### 1.2 任务类型扩展
```
任务类型
├── 脚本执行 (现有)
├── 文件传输 (新增)
├── 文件同步 (扩展)
└── 混合任务 (脚本+文件)
```

**建议优化**:
- **传输模式**: 
  - 单向传输（服务器→客户端）
  - 双向同步（可选）
  - 增量传输（只传输变更部分）
- **传输策略**:
  - 覆盖模式：直接覆盖目标文件
  - 备份模式：备份原文件后替换
  - 版本模式：保留多个版本

### 2. 数据模型设计

#### 2.1 文件管理表
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
    checksum VARCHAR(64) COMMENT 'SHA256校验和',
    description TEXT COMMENT '文件描述',
    tags VARCHAR(500) COMMENT '标签，逗号分隔',
    upload_by VARCHAR(100) NOT NULL COMMENT '上传者',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_file_id (file_id),
    INDEX idx_category (category),
    INDEX idx_upload_by (upload_by)
);
```

#### 2.2 任务表扩展
```sql
-- 扩展任务表，添加任务类型
ALTER TABLE tasks ADD COLUMN task_type VARCHAR(20) DEFAULT 'SCRIPT' COMMENT '任务类型：SCRIPT, FILE_TRANSFER';

-- 文件传输任务配置表
CREATE TABLE file_transfer_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(50) NOT NULL COMMENT '关联任务ID',
    source_file_id VARCHAR(50) NOT NULL COMMENT '源文件ID',
    target_path VARCHAR(500) NOT NULL COMMENT '目标路径',
    transfer_mode VARCHAR(20) DEFAULT 'OVERWRITE' COMMENT '传输模式：OVERWRITE, BACKUP, VERSION',
    verify_checksum BOOLEAN DEFAULT TRUE COMMENT '是否校验文件完整性',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (task_id) REFERENCES tasks(task_id),
    FOREIGN KEY (source_file_id) REFERENCES files(file_id)
);
```

#### 2.3 传输结果表
```sql
-- 文件传输结果表
CREATE TABLE file_transfer_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id VARCHAR(50) NOT NULL COMMENT '执行ID',
    agent_id VARCHAR(50) NOT NULL COMMENT 'Agent ID',
    file_id VARCHAR(50) NOT NULL COMMENT '文件ID',
    target_path VARCHAR(500) NOT NULL COMMENT '目标路径',
    transfer_status VARCHAR(20) NOT NULL COMMENT '传输状态：SUCCESS, FAILED, PARTIAL',
    file_size BIGINT COMMENT '实际传输大小',
    checksum_verified BOOLEAN COMMENT '校验是否通过',
    error_message TEXT COMMENT '错误信息',
    transfer_time BIGINT COMMENT '传输耗时（毫秒）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_execution_id (execution_id),
    INDEX idx_agent_id (agent_id)
);
```

### 3. API设计

#### 3.1 文件管理API
```java
@RestController
@RequestMapping("/api/web/files")
public class FileController {
    
    // 上传文件
    @PostMapping("/upload")
    public FileDTO uploadFile(@RequestParam("file") MultipartFile file,
                             @RequestParam("category") String category,
                             @RequestParam(value = "description", required = false) String description);
    
    // 获取文件列表
    @GetMapping
    public Page<FileDTO> getFiles(@RequestParam(required = false) String category,
                                 @RequestParam(required = false) String keyword);
    
    // 下载文件
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileId);
    
    // 获取文件信息
    @GetMapping("/{fileId}")
    public FileDTO getFileInfo(@PathVariable String fileId);
    
    // 删除文件
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable String fileId);
}
```

#### 3.2 文件传输任务API
```java
// 扩展TaskController
@PostMapping("/file-transfer")
public TaskDTO createFileTransferTask(@RequestBody CreateFileTransferTaskRequest request);

// Agent API扩展
@GetMapping("/files/{fileId}/download")
public ResponseEntity<Resource> downloadFileForAgent(@PathVariable String fileId,
                                                    @RequestHeader("Agent-ID") String agentId);

@PostMapping("/file-transfer/result")
public ResponseEntity<Void> reportTransferResult(@RequestBody FileTransferResultRequest request);
```

### 4. 前端界面设计

#### 4.1 导航菜单扩展
```javascript
// 添加文件管理菜单
{
  key: 'files',
  icon: <FileOutlined />,
  label: '文件管理',
  path: '/files'
}
```

#### 4.2 文件管理页面
```
文件管理页面
├── 工具栏
│   ├── 上传文件按钮
│   ├── 搜索框
│   └── 分类筛选
├── 文件列表
│   ├── 文件信息（名称、大小、类型、上传时间）
│   ├── 操作按钮（下载、删除、查看详情）
│   └── 批量操作
└── 文件详情模态框
    ├── 基本信息
    ├── 版本历史
    └── 使用记录
```

#### 4.3 任务创建页面扩展
```javascript
// 任务类型选择
<Select placeholder="选择任务类型">
  <Option value="SCRIPT">脚本执行</Option>
  <Option value="FILE_TRANSFER">文件传输</Option>
</Select>

// 文件传输配置
{taskType === 'FILE_TRANSFER' && (
  <div>
    <Form.Item label="源文件">
      <Select placeholder="选择要传输的文件">
        {files.map(file => (
          <Option key={file.fileId} value={file.fileId}>
            {file.name} ({file.sizeDisplay})
          </Option>
        ))}
      </Select>
    </Form.Item>
    
    <Form.Item label="目标路径">
      <Input placeholder="例如：/opt/app/config.json" />
    </Form.Item>
    
    <Form.Item label="传输模式">
      <Radio.Group>
        <Radio value="OVERWRITE">直接覆盖</Radio>
        <Radio value="BACKUP">备份后覆盖</Radio>
        <Radio value="VERSION">版本管理</Radio>
      </Radio.Group>
    </Form.Item>
  </div>
)}
```

### 5. 执行流程设计

#### 5.1 文件传输任务执行流程
```
1. 任务创建
   ├── 选择源文件
   ├── 配置目标路径
   └── 设置传输参数

2. 任务分发
   ├── 服务器验证文件存在性
   ├── 生成下载令牌
   └── 发送任务到Agent

3. Agent执行
   ├── 接收任务指令
   ├── 从服务器下载文件
   ├── 验证文件完整性
   ├── 保存到目标路径
   └── 报告执行结果

4. 结果收集
   ├── 收集各Agent执行结果
   ├── 更新任务状态
   └── 生成执行报告
```

#### 5.2 安全考虑
```
安全措施
├── 文件访问控制
│   ├── 基于角色的文件访问权限
│   ├── 文件下载令牌机制
│   └── Agent身份验证
├── 文件完整性
│   ├── 上传时计算校验和
│   ├── 下载后验证校验和
│   └── 传输过程中的错误检测
└── 路径安全
    ├── 目标路径白名单
    ├── 路径遍历攻击防护
    └── 文件权限控制
```

### 6. 考虑不周的地方和改进建议

#### 6.1 需要补充的功能
1. **断点续传**: 大文件传输中断后可以续传
2. **并发控制**: 限制同时进行的文件传输数量
3. **带宽限制**: 避免文件传输占用过多网络带宽
4. **传输进度**: 实时显示文件传输进度
5. **文件预览**: 支持文本文件、图片等在线预览
6. **批量传输**: 支持一次传输多个文件
7. **定时传输**: 支持定时执行文件传输任务

#### 6.2 性能优化
1. **文件分块**: 大文件分块传输，提高成功率
2. **压缩传输**: 支持文件压缩传输，节省带宽
3. **缓存机制**: Agent端缓存已下载的文件
4. **增量同步**: 只传输文件的变更部分

#### 6.3 监控和日志
1. **传输监控**: 实时监控文件传输状态和进度
2. **性能统计**: 统计传输速度、成功率等指标
3. **审计日志**: 记录文件上传、下载、删除等操作
4. **告警机制**: 传输失败时及时告警

### 7. 实施建议

#### 7.1 分阶段实施
```
第一阶段：基础功能
├── 文件管理模块
├── 基础文件传输任务
└── 简单的执行结果报告

第二阶段：增强功能  
├── 文件版本管理
├── 传输进度显示
└── 批量操作

第三阶段：高级功能
├── 断点续传
├── 增量同步
└── 性能优化
```

#### 7.2 技术选型建议
- **文件存储**: 本地文件系统 + 数据库元信息
- **传输协议**: HTTP/HTTPS（复用现有通信机制）
- **文件校验**: SHA256算法
- **进度跟踪**: WebSocket实时通信

### 8. 风险评估

#### 8.1 技术风险
- **存储空间**: 需要考虑服务器存储容量限制
- **网络带宽**: 大文件传输可能影响系统性能
- **并发处理**: 多个Agent同时下载可能造成服务器压力

#### 8.2 安全风险
- **文件安全**: 恶意文件上传和下载
- **路径遍历**: 目标路径设置不当可能覆盖系统文件
- **权限控制**: 需要严格的文件访问权限管理

#### 8.3 缓解措施
- 设置文件大小和类型限制
- 实施严格的路径白名单机制
- 添加文件扫描和安全检查
- 实现细粒度的权限控制

## 总结

这个文件传输功能是对现有系统的很好补充，建议：

1. **优先实现基础功能**，确保核心流程可用
2. **重视安全设计**，防止安全漏洞
3. **考虑性能影响**，避免影响现有功能
4. **分阶段实施**，逐步完善功能
5. **充分测试**，特别是大文件和并发场景

这个功能将大大增强系统的实用性，特别适合配置文件分发、软件包部署等场景。