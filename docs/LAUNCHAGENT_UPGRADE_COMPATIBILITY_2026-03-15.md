# LaunchAgent与自动升级兼容性修复

**日期**: 2026-03-15  
**版本**: v0.4.0  
**状态**: ✅ 已修复

## 问题发现

LaunchAgent的`KeepAlive=true`配置可能与Agent自动升级产生冲突：

### 潜在问题场景
1. **Agent升级流程**：下载新版本 → 停止当前进程 → 替换文件 → 重启
2. **LaunchAgent行为**：检测到进程退出 → 立即重启旧版本
3. **竞争条件**：升级器和LaunchAgent同时尝试启动Agent

### 可能的后果
- 升级器启动新版本，LaunchAgent启动旧版本
- 升级失败，因为LaunchAgent重启了旧版本
- 两个Agent实例冲突（单实例锁会阻止）

## 解决方案

### 1. 升级前禁用LaunchAgent
在`UpgradeExecutor.executeUpgrade()`中添加：
```java
// 临时禁用LaunchAgent自动重启
disableLaunchAgentAutoRestart();
```

### 2. 升级后重新启用LaunchAgent
在`AgentUpgrader.startNewVersion()`中添加：
```java
// 重新启用LaunchAgent
reEnableLaunchAgent();
```

### 3. 智能检测和处理
- 自动检测用户级和系统级LaunchAgent
- 使用`launchctl unload/load`命令管理
- 提供详细的日志记录

## 技术实现

### UpgradeExecutor新增方法
```java
private void disableLaunchAgentAutoRestart() {
    // 检查并卸载用户级LaunchAgent
    // 检查并卸载系统级LaunchDaemon
    // 记录操作日志
}
```

### AgentUpgrader新增方法
```java
private void reEnableLaunchAgent() {
    // 检查并重新加载LaunchAgent/LaunchDaemon
    // 确保升级后服务正常运行
}
```

## 升级流程优化

### 修改前的流程
```
Agent收到升级通知 → 下载新版本 → 启动升级器 → 退出
                                    ↓
LaunchAgent检测到退出 → 立即重启旧版本 (冲突!)
                                    ↓
升级器替换文件 → 启动新版本 (可能失败)
```

### 修改后的流程
```
Agent收到升级通知 → 下载新版本 → 禁用LaunchAgent → 启动升级器 → 退出
                                                      ↓
升级器替换文件 → 启动新版本 → 重新启用LaunchAgent → 升级完成
```

## 部署状态

✅ **代码修改**: UpgradeExecutor和AgentUpgrader已修改  
🔄 **测试验证**: 需要实际升级场景测试  
🔄 **构建部署**: 待重新构建和部署  

---

**修复完成**: 2026-03-15 23:15  
**下一步**: 构建部署并进行升级测试