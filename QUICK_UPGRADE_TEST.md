# Agent升级快速测试指南

## 当前状态
✅ 测试文件已准备完成
✅ 文件服务器已启动 (http://localhost:8000)

## 测试文件信息
- **agent-1.0.0.jar**: 5,446,381 bytes
  - SHA256: `4c113d84224c9018c4b433aea08e8387c4f1440affe2d23d851abfa0280e8a72`
  - URL: http://localhost:8000/agent-1.0.0.jar

- **agent-1.1.0.jar**: 5,446,412 bytes  
  - SHA256: `1b8d6b558bb3c8cdeaf81bc61a49a35670fdc92b8f5d215e63299d6ae0415668`
  - URL: http://localhost:8000/agent-1.1.0.jar

## 快速测试步骤

### 1. 启动LightScript服务
```bash
# 终端1: 启动服务端
cd server
mvn spring-boot:run

# 终端2: 启动Agent
cd agent  
java -jar target/agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar http://localhost:8080 dev-register-token
```

### 2. 创建升级版本
1. 访问: http://localhost:3000/agent-versions
2. 点击"新建版本"
3. 填写信息:
   ```
   版本号: 1.1.0
   下载地址: http://localhost:8000/agent-1.1.0.jar
   文件大小: 5446412
   文件哈希: 1b8d6b558bb3c8cdeaf81bc61a49a35670fdc92b8f5d215e63299d6ae0415668
   平台: ALL
   是否最新版本: ✓
   发布说明: 测试升级版本
   ```

### 3. 观察升级过程
Agent控制台将显示:
```
[VersionCheck] Update available: Update available
[VersionCheck] No running tasks, starting upgrade...
[UpgradeExecutor] Starting upgrade: 1.0.0 -> 1.1.0
[UpgradeReporter] Reported upgrade start: 1.0.0 -> 1.1.0, logId: 1
[UpgradeExecutor] Downloading new version from: http://localhost:8000/agent-1.1.0.jar
[UpgradeExecutor] New version downloaded and verified
[UpgradeExecutor] Upgrader started
[UpgradeExecutor] Upgrade initiated, main process exiting...
```

### 4. 验证升级结果
1. 检查Agent是否重新启动
2. 在Web界面查看Agent版本是否更新为1.1.0
3. 查看升级历史记录

## 测试变体

### 强制升级测试
在创建版本时勾选"强制升级"，即使有任务执行也会在5分钟后强制升级

### 任务保护测试
1. 先创建一个长时间运行的任务:
   ```bash
   sleep 300  # 5分钟
   ```
2. 然后触发升级，观察升级被延迟

### 升级失败测试
使用无效的下载URL或错误的哈希值测试失败处理

## 监控点
- Agent控制台日志
- 升级器日志: `upgrade.log`, `upgrade-error.log`  
- 新版本启动日志: `agent-startup.log`
- Web界面的升级历史
- 数据库中的升级记录

## 清理
测试完成后停止文件服务器:
```bash
# 查找并停止Python服务器进程
ps aux | grep "python3 -m http.server"
kill [PID]
```