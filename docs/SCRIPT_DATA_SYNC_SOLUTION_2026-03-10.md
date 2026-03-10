# 脚本数据同步解决方案

## 问题描述
用户反馈：创建任务时，从已有脚本中选不到刚刚上传的脚本。

## 根本原因
- Tasks.jsx中的`fetchAvailableScripts`函数使用硬编码的脚本数据
- Scripts.jsx和Tasks.jsx之间没有数据共享机制
- 两个页面各自维护独立的脚本数据状态

## 解决方案

### 1. 创建scriptService.js数据管理服务
- 实现单例模式的脚本数据管理服务
- 提供统一的数据接口：getAllScripts、getScriptsForTask、addScript、updateScript、deleteScript
- 实现观察者模式，支持数据变化监听
- 自动生成脚本ID，避免重复

### 2. 修改Scripts.jsx使用scriptService
- 移除硬编码的脚本数据
- 使用scriptService管理所有脚本操作
- 添加数据变化监听，实时更新UI
- 所有CRUD操作通过scriptService进行

### 3. 修改Tasks.jsx使用scriptService
- 修改fetchAvailableScripts函数从scriptService获取数据
- 添加脚本数据变化监听
- 确保创建任务时能获取最新的脚本列表

## 技术实现

### scriptService.js核心功能
```javascript
class ScriptService {
  constructor() {
    this.scripts = [...] // 初始数据
    this.listeners = []  // 监听器列表
    this.nextId = 4     // ID生成器
  }
  
  // 数据操作方法
  getAllScripts()
  getScriptsForTask() 
  addScript(script)
  updateScript(key, updates)
  deleteScript(key)
  
  // 监听器管理
  addListener(callback)
  removeListener(callback)
  notifyListeners()
}
```

### 数据同步机制
1. Scripts页面操作脚本时，通过scriptService更新数据
2. scriptService通知所有监听器数据已变化
3. Tasks页面监听到变化，自动刷新可用脚本列表
4. 用户在创建任务时能看到最新的脚本数据

## 修改文件列表
- `web-modern/src/services/scriptService.js` - 新建数据管理服务
- `web-modern/src/pages/Scripts.jsx` - 集成scriptService
- `web-modern/src/pages/Tasks.jsx` - 使用scriptService获取脚本数据

## 测试验证
1. 在Scripts页面创建新脚本
2. 切换到Tasks页面创建任务
3. 验证新脚本出现在"选择已有脚本"列表中
4. 验证上传脚本和手动录入脚本都能正常同步

## 效果
- ✅ 解决了脚本数据不同步的问题
- ✅ 实现了两个页面之间的实时数据共享
- ✅ 保持了现有功能的完整性
- ✅ 提供了可扩展的数据管理架构

## 后续优化建议
1. 考虑将scriptService与后端API集成
2. 添加本地存储支持，避免刷新页面丢失数据
3. 实现脚本版本管理功能
4. 添加脚本使用统计和分析功能