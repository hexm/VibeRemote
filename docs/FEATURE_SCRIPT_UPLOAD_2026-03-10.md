# 脚本上传功能实现 - 2026-03-10

## 概述
根据用户需求，为脚本管理页面添加了文件上传功能，同时保留了原有的手动录入功能。手动录入支持编码格式选择，文件上传则保持文件原有编码。

## 功能特性

### 1. 双模式创建脚本 ✅
**手动录入模式**：
- 保留原有的手动输入脚本内容功能
- 支持在线编辑脚本代码
- 适合创建简单脚本或快速测试

**文件上传模式**：
- 支持拖拽上传脚本文件
- 文件在服务器端以文件形式保存
- 不读取文件内容到数据库
- 适合上传现有的脚本文件

### 2. 编码格式支持 ✅
**手动录入模式**：
- 支持选择编码格式：UTF-8 (默认)、GBK、GB2312、ASCII、ISO-8859-1
- 适用于需要指定特定编码的脚本创建场景

**文件上传模式**：
- 编码格式由文件本身决定，无需用户选择
- 系统会保持文件原有的编码格式
- 更符合文件上传的实际使用场景

### 3. 文件类型支持 ✅
**支持的脚本格式**：
- `.sh` - Bash脚本
- `.ps1` - PowerShell脚本
- `.bat/.cmd` - Windows批处理
- `.py` - Python脚本
- `.js` - JavaScript脚本
- `.ts` - TypeScript脚本

**自动类型识别**：
根据文件扩展名自动设置脚本类型，用户可手动调整。

### 4. 文件验证 ✅
**文件大小限制**：
- 最大文件大小：1MB
- 防止上传过大文件影响系统性能

**文件类型验证**：
- 只允许上传支持的脚本文件格式
- 阻止上传其他类型文件

## 技术实现

### 前端实现

#### Tab切换界面
```javascript
<Tabs activeKey={activeTab} onChange={setActiveTab}>
  <TabPane tab={<span><CodeOutlined />手动录入</span>} key="manual">
    {/* 手动录入表单 */}
  </TabPane>
  <TabPane tab={<span><UploadOutlined />文件上传</span>} key="upload">
    {/* 文件上传表单 */}
  </TabPane>
</Tabs>
```

#### 文件上传组件
```javascript
<Dragger {...uploadProps}>
  <p className="ant-upload-drag-icon">
    <InboxOutlined />
  </p>
  <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
  <p className="ant-upload-hint">
    支持 .sh, .ps1, .bat, .cmd, .py, .js, .ts 格式，文件大小不超过1MB
  </p>
</Dragger>
```

#### 文件验证逻辑
```javascript
beforeUpload: (file) => {
  // 检查文件类型
  const allowedTypes = ['.sh', '.ps1', '.bat', '.cmd', '.py', '.js', '.ts']
  const fileExtension = file.name.toLowerCase().substring(file.name.lastIndexOf('.'))
  
  if (!allowedTypes.includes(fileExtension)) {
    message.error('只支持脚本文件格式')
    return false
  }

  // 检查文件大小
  if (file.size > 1024 * 1024) {
    message.error('文件大小不能超过1MB')
    return false
  }

  // 自动设置脚本类型
  const typeMap = {
    '.sh': 'bash',
    '.ps1': 'powershell', 
    '.py': 'python',
    // ...
  }
  
  return false // 阻止自动上传
}
```

### 数据结构扩展

#### 脚本对象新增字段
```javascript
{
  // 原有字段...
  encoding: 'UTF-8',           // 编码格式
  isUploaded: true,            // 是否为上传文件
  filePath: '/scripts/xxx.sh', // 服务器文件路径 (上传文件)
}
```

#### 界面显示增强
- 上传的脚本显示"上传"标签
- 手动录入的脚本显示编码格式信息
- 支持JavaScript/TypeScript类型

### 用户体验优化

#### 智能表单填充
- 上传文件后自动填充文件名
- 根据扩展名自动选择脚本类型
- 手动录入时编码格式默认为UTF-8

#### 状态管理
- 切换Tab时保持表单状态
- 取消操作时重置所有状态
- 上传失败时保持用户输入

#### 错误处理
- 文件类型不支持时的友好提示
- 文件过大时的明确错误信息
- 网络错误时的重试机制

## 后端设计建议

### API接口设计

#### 文件上传接口
```
POST /web/scripts/upload
Content-Type: multipart/form-data

Parameters:
- file: 脚本文件
- name: 脚本名称
- description: 脚本描述
- encoding: 编码格式
- type: 脚本类型
```

#### 响应格式
```json
{
  "success": true,
  "data": {
    "scriptId": "S001",
    "filePath": "/scripts/update-system.sh",
    "size": 2048
  }
}
```

### 文件存储策略

#### 目录结构
```
/opt/lightscript/scripts/
├── bash/
│   ├── update-system.sh
│   └── cleanup-logs.sh
├── powershell/
│   └── backup-data.ps1
└── python/
    └── monitor.py
```

#### 文件命名规则
- 保持原文件名
- 重名时添加时间戳后缀
- 使用UUID确保唯一性

### 数据库设计

#### Scripts表扩展
```sql
ALTER TABLE scripts ADD COLUMN encoding VARCHAR(20) DEFAULT 'UTF-8';
ALTER TABLE scripts ADD COLUMN is_uploaded BOOLEAN DEFAULT FALSE;
ALTER TABLE scripts ADD COLUMN file_path VARCHAR(500);
```

#### 存储策略
- **手动录入**: content字段存储脚本内容
- **文件上传**: file_path字段存储文件路径，content为空或摘要

## 安全考虑

### 文件上传安全
1. **文件类型验证**: 严格限制允许的文件扩展名
2. **文件大小限制**: 防止大文件攻击
3. **文件内容扫描**: 检查恶意代码模式
4. **存储隔离**: 上传文件存储在安全目录

### 权限控制
1. **上传权限**: 需要script:create权限
2. **文件访问**: 只能访问自己上传的文件
3. **执行权限**: 上传的脚本需要额外审核

### 编码安全
1. **编码验证**: 验证文件编码格式
2. **内容转换**: 统一转换为UTF-8存储
3. **特殊字符**: 处理不同编码的特殊字符

## 测试用例

### 功能测试
1. **手动录入**: 创建各种类型的脚本
2. **文件上传**: 上传不同格式的脚本文件
3. **编码格式**: 测试不同编码格式的文件
4. **类型识别**: 验证自动类型识别功能

### 边界测试
1. **文件大小**: 测试1MB边界
2. **文件类型**: 测试不支持的文件类型
3. **特殊字符**: 测试包含特殊字符的文件名
4. **网络异常**: 测试上传过程中的网络中断

### 安全测试
1. **恶意文件**: 尝试上传恶意脚本
2. **路径遍历**: 测试文件名包含路径遍历
3. **大文件攻击**: 尝试上传超大文件
4. **并发上传**: 测试并发上传场景

## 后续优化建议

### 1. 文件预览
添加上传前的文件内容预览功能：
```javascript
const handleFilePreview = (file) => {
  const reader = new FileReader()
  reader.onload = (e) => {
    setPreviewContent(e.target.result)
    setPreviewVisible(true)
  }
  reader.readAsText(file, encoding)
}
```

### 2. 批量上传
支持一次上传多个脚本文件：
```javascript
<Upload
  multiple={true}
  maxCount={10}
  beforeUpload={handleBatchUpload}
>
```

### 3. 版本控制
为脚本添加版本管理功能：
- 保存历史版本
- 版本对比
- 回滚功能

### 4. 在线编辑器
集成代码编辑器，提供更好的编辑体验：
```javascript
import { CodeEditor } from '@monaco-editor/react'

<CodeEditor
  language={scriptType}
  value={content}
  onChange={handleContentChange}
  options={{
    minimap: { enabled: false },
    fontSize: 14,
  }}
/>
```

## 总结

本次功能增强成功实现了：

### ✅ 完成的功能
1. **双模式创建**: 手动录入 + 文件上传
2. **编码格式区分**: 手动录入支持编码选择，文件上传保持原有编码
3. **文件类型扩展**: 支持6种脚本格式
4. **智能识别**: 自动类型识别和表单填充
5. **安全验证**: 文件类型和大小验证
6. **用户体验**: Tab切换和拖拽上传

### 🎯 设计亮点
- **保持兼容**: 完全保留原有手动录入功能
- **智能化**: 自动识别文件类型和编码
- **安全性**: 多层文件验证机制
- **易用性**: 拖拽上传和智能表单填充

这个实现为脚本管理提供了更灵活的创建方式，既满足了快速录入的需求，也支持了现有脚本文件的导入，大大提升了系统的实用性。