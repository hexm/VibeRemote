# 待处理问题清单

## 日期：2026-03-09

---

## 🐛 UI问题

### 1. 创建任务表单 - Radio按钮垂直排列问题

**问题描述**：
- 创建任务对话框中的"选择方式"和"脚本来源"单选按钮组垂直排列
- 期望：单选按钮应该在同一行水平排列

**当前状态**：
- 已尝试多种方法：
  - 使用Space组件包裹
  - 使用Tailwind CSS的flex和gap类
  - 使用inline样式 `style={{ display: 'inline-flex', gap: '16px' }}`
- 所有方法都未生效

**可能原因**：
- Ant Design的Radio.Group可能有更深层的CSS样式覆盖
- 可能需要使用!important或更具体的CSS选择器
- 可能需要检查是否有全局CSS影响

**建议解决方案**：
1. 在index.css中添加全局样式覆盖
2. 使用CSS Modules或styled-components
3. 检查Ant Design版本和文档，确认正确的水平布局方式
4. 使用浏览器开发者工具检查实际应用的CSS规则

**影响范围**：
- 文件：`web-modern/src/pages/Tasks.jsx`
- 影响：UI美观性，不影响功能

**优先级**：低（不影响功能使用）

---

## ✅ 已修复问题

### 1. Ant Design message警告 - 已修复 (2026-03-09)
**问题**：Warning: [antd: message] Static function can not consume context like dynamic theme
**解决方案**：
- 在main.jsx中添加`<AntApp>`组件包裹
- 在App.jsx中使用`AntApp.useApp()`获取message实例
- 文件：`web-modern/src/main.jsx`, `web-modern/src/App.jsx`

### 2. 客户端分组页面白屏 - 已修复 (2026-03-09)
**问题**：打开客户端分组页面后退出会导致白屏
**解决方案**：
- 使用useRef跟踪组件挂载状态
- 在异步操作中检查组件是否仍然挂载
- 添加useEffect清理函数
- 文件：`web-modern/src/pages/AgentGroups.jsx`
- 文档：`docs/BUGFIX_AGENT_GROUPS_WHITE_SCREEN_2026-03-09.md`

---

## 📝 功能需求（未完成）

### 1. 脚本管理参数化功能
**状态**：未实现  
**完成度**：40%  
**详情**：见 `docs/FEATURE_IMPLEMENTATION_STATUS.md`

### 2. 批量任务串行执行
**状态**：需重做  
**完成度**：20%  
**详情**：见 `docs/FEATURE_IMPLEMENTATION_STATUS.md`

---

## 更新记录

- 2026-03-09：创建待处理问题清单
- 2026-03-09：添加Radio按钮垂直排列问题
- 2026-03-09：修复Ant Design message警告
- 2026-03-09：修复客户端分组页面白屏问题
