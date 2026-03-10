# 任务创建界面优化

## 优化日期
2026-03-09

## 优化内容

### 1. 任务名称自动生成
- 使用时间日期自动生成任务名称
- 格式：`任务_20260309_143025`（年月日_时分秒）
- 用户可以修改自动生成的名称

### 2. 表单分组优化
将创建任务表单分为四个清晰的分组，每个分组有独特的颜色标识：

#### 基本信息（蓝色）
- 任务名称（必填，自动生成）
- 任务描述（可选，最多500字）

#### 执行客户端（绿色）
- 选择方式（手动选择/按分组选择）
- 选择分组（当选择按分组时显示）
- 目标节点（可多选）

#### 脚本内容（紫色）
- 脚本来源（自定义输入/选择已有脚本）
- 选择脚本（当选择已有脚本时显示）
- 脚本类型（Shell/Python/JavaScript）
- 脚本内容（代码编辑器）

#### 执行参数（橙色）
- 超时时间（秒）
- 启动选项（立即启动/保存为草稿）

### 3. 布局优化
- 模态框宽度从700px增加到800px，提供更好的视觉空间
- 使用Row和Col进行响应式布局
- 任务名称和描述使用全宽布局
- 脚本类型和超时时间使用半宽布局（12/24）
- 输入框不再过度拉伸，保持合理宽度

### 4. 视觉改进
- 每个分组使用彩色竖线标识
- 分组标题使用加粗字体
- 内容区域使用左侧缩进（pl-3）
- 统一的间距（mb-6用于分组，space-y-4用于表单项）
- 多选下拉框使用maxTagCount="responsive"自适应显示

## 技术实现

### 自动生成任务名称
```javascript
initialValues={{
  timeoutSec: 300,
  scriptLang: 'shell',
  taskName: `任务_${new Date().toLocaleString('zh-CN', { 
    year: 'numeric', 
    month: '2-digit', 
    day: '2-digit', 
    hour: '2-digit', 
    minute: '2-digit',
    second: '2-digit',
    hour12: false 
  }).replace(/\//g, '').replace(/:/g, '').replace(/\s/g, '_')}`
}}
```

### 分组样式
```jsx
<div className="mb-6">
  <div className="flex items-center mb-3">
    <div className="w-1 h-5 bg-blue-500 mr-2"></div>
    <Text strong className="text-base">基本信息</Text>
  </div>
  <div className="pl-3 space-y-4">
    {/* 表单项 */}
  </div>
</div>
```

### 响应式布局
```jsx
<Row gutter={16}>
  <Col span={12}>
    <Form.Item name="timeoutSec" label="超时时间（秒）">
      <Input type="number" min={1} placeholder="默认300秒" />
    </Form.Item>
  </Col>
  <Col span={12}>
    <Form.Item name="autoStart" label="启动选项">
      {/* Switch组件 */}
    </Form.Item>
  </Col>
</Row>
```

## 用户体验提升

1. **清晰的信息层次**：四个分组让用户一目了然地了解需要填写的内容
2. **自动化输入**：任务名称自动生成，减少用户输入负担
3. **合理的布局**：不同类型的输入项使用不同的宽度，避免过度拉伸
4. **视觉引导**：彩色标识帮助用户快速定位不同的配置区域
5. **响应式设计**：使用Ant Design的栅格系统，适配不同屏幕尺寸

## 文件修改

- `web-modern/src/pages/Tasks.jsx` - 优化创建任务表单

## 测试建议

1. 打开创建任务对话框，验证任务名称是否自动生成
2. 检查四个分组是否清晰显示，颜色标识是否正确
3. 测试不同输入项的宽度是否合理
4. 验证表单验证规则是否正常工作
5. 测试响应式布局在不同屏幕尺寸下的表现

## 后续优化建议

1. 可以考虑添加任务名称模板配置
2. 可以添加更多的预设脚本模板
3. 可以添加脚本内容的语法高亮
4. 可以添加表单保存草稿功能（自动保存）
