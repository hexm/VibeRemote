# Agent自动升级测试指南

## 文档信息
- **创建时间**: 2026-03-11
- **版本**: 1.0
- **状态**: 测试指南
- **作者**: 系统架构师

## 1. 升级文件格式和来源

### 1.1 文件格式
- **文件类型**: JAR文件 (Java Archive)
- **命名规范**: `agent-{version}.jar` (例如: `agent-1.1.0.jar`)
- **文件大小**: 通常几MB到几十MB
- **哈希验证**: SHA256哈希值用于完整性验证

### 1.2 文件来源

#### 方式1: 文件管理系统
```
1. 通过Web界面上传JAR文件到文件管理系统
2. 在Agent版本管理页面创建版本记录
3. 关联上传的文件ID
4. downloadUrl格式: /api/agent/files/{fileId}/download
```

#### 方式2: 外部HTTP URL
```
直接指向可访问的HTTP/HTTPS下载链接
例如: https://releases.example.com/agent-1.1.0.jar
或本地测试: http://localhost:8000/agent-1.1.0.jar
```

## 2. 准备测试环境

### 2.1 创建测试升级包

运行测试包创建脚本：
```bash
./create-test-upgrade.sh
```

这将创建：
- `test-upgrade/agent-1.0.0.jar` - 当前版本
- `test-upgrade/agent-1.1.0.jar` - 新版本
- `test-upgrade/version-info.json` - 版本信息
- `test-upgrade/start-file-server.sh` - 文件服务器

### 2.2 启动文件服务器

```bash
cd test-upgrade
./start-file-server.sh
```

访问 http://localhost:8000 验证文件可下载

### 2.3 启动LightScript服务

```bash
# 启动服务端
cd server
mvn spring-boot:run

# 启动Agent (新终端)
cd agent
java -jar target/agent-*.jar http://localhost:8080 dev-register-token
```

## 3. 测试场景

### 3.1 场景1: 通过外部URL升级

#### 步骤1: 创建版本记录
1. 访问 http://localhost:3000/agent-versions
2. 点击"新建版本"
3. 填写版本信息：
   ```
   版本号: 1.1.0
   下载地址: http://localhost:8000/agent-1.1.0.jar
   文件大小: [从version-info.json获取]
   文件哈希: [从version-info.json获取]
   平台: ALL
   是否最新版本: ✓
   强制升级: □ (可选)
   发布说明: 测试升级版本
   ```

#### 步骤2: 观察升级过程
1. 查看Agent控制台日志
2. 等待心跳检测到更新（最多30秒）
3. 观察升级流程：
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

#### 步骤3: 验证升级结果
1. 检查升级器日志：`upgrade.log`, `upgrade-error.log`
2. 检查新Agent是否启动：`agent-startup.log`
3. 在Web界面查看Agent版本是否更新
4. 查看升级历史记录

### 3.2 场景2: 通过文件管理系统升级

#### 步骤1: 上传文件
1. 访问 http://localhost:3000/files
2. 上传 `agent-1.1.0.jar`
3. 记录文件ID

#### 步骤2: 创建版本记录
1. 访问 http://localhost:3000/agent-versions
2. 创建版本，下载地址使用：`/api/agent/files/{fileId}/download`

#### 步骤3: 观察升级过程（同场景1）

### 3.3 场景3: 有任务执行时的升级

#### 步骤1: 创建长时间运行的任务
1. 访问 http://localhost:3000/tasks
2. 创建脚本任务：
   ```bash
   # Linux/macOS
   sleep 300  # 5分钟
   
   # Windows
   timeout /t 300
   ```

#### 步骤2: 触发升级
1. 创建新版本记录（同场景1）
2. 观察Agent日志：
   ```
   [VersionCheck] Update available: Update available
   [VersionCheck] Tasks are running, upgrade will be delayed
   [UpgradeExecutor] Tasks are running, scheduling upgrade retry in 30 minutes
   ```

#### 步骤3: 等待任务完成
1. 任务完成后，升级自动开始
2. 或者测试强制升级（设置forceUpgrade=true，5分钟后强制升级）

### 3.4 场景4: 升级失败回滚

#### 步骤1: 创建有问题的升级包
```bash
# 创建一个无效的JAR文件
echo "invalid jar content" > test-upgrade/agent-1.2.0-broken.jar
```

#### 步骤2: 创建版本记录
使用无效文件的URL创建版本记录

#### 步骤3: 观察回滚过程
1. 升级器检测到启动失败
2. 自动回滚到备份版本
3. 查看升级状态为"ROLLBACK"

## 4. 监控和验证

### 4.1 日志文件
- **Agent主日志**: 控制台输出
- **升级器日志**: `upgrade.log`
- **升级器错误**: `upgrade-error.log`
- **新版本启动**: `agent-startup.log`
- **新版本错误**: `agent-startup-error.log`

### 4.2 数据库检查
```sql
-- 查看升级日志
SELECT * FROM agent_upgrade_logs ORDER BY created_at DESC;

-- 查看Agent版本
SELECT agent_id, hostname, agent_version, status FROM agents;

-- 查看版本配置
SELECT * FROM agent_versions ORDER BY created_at DESC;
```

### 4.3 Web界面验证
1. **Agent列表**: 查看版本列显示
2. **Agent详情**: 查看升级历史
3. **版本管理**: 查看版本配置
4. **升级监控**: 查看升级状态

## 5. 常见问题和解决方案

### 5.1 下载失败
**问题**: 文件下载失败
**解决**: 
- 检查URL是否可访问
- 验证文件大小和哈希
- 检查网络连接

### 5.2 升级器启动失败
**问题**: upgrader.jar不存在
**解决**:
```bash
# 确保升级器存在
cp upgrader/target/upgrader.jar agent/
```

### 5.3 权限问题
**问题**: 文件替换权限不足
**解决**:
- 确保Agent有写权限
- 检查文件锁定状态

### 5.4 版本检查不生效
**问题**: Agent不检测更新
**解决**:
- 检查心跳是否正常
- 验证版本比较逻辑
- 确认isLatest标记正确

## 6. 性能测试

### 6.1 大文件下载测试
```bash
# 创建大文件测试包（50MB）
dd if=/dev/zero of=test-upgrade/agent-1.3.0-large.jar bs=1M count=50
```

### 6.2 并发升级测试
1. 启动多个Agent实例
2. 同时触发升级
3. 观察服务器负载和升级成功率

### 6.3 网络中断测试
1. 升级过程中断开网络
2. 验证重试机制
3. 检查状态报告

## 7. 自动化测试脚本

### 7.1 升级测试脚本
```bash
#!/bin/bash
# test-upgrade-flow.sh

echo "开始自动升级测试..."

# 1. 启动文件服务器
cd test-upgrade
./start-file-server.sh &
SERVER_PID=$!

# 2. 等待服务器启动
sleep 2

# 3. 创建版本记录（通过API）
curl -X POST http://localhost:8080/web/agent-versions \
  -H "Content-Type: application/json" \
  -d '{
    "version": "1.1.0",
    "downloadUrl": "http://localhost:8000/agent-1.1.0.jar",
    "fileSize": 12345,
    "fileHash": "abc123...",
    "isLatest": true,
    "platform": "ALL"
  }'

# 4. 等待升级完成
echo "等待升级完成..."
sleep 60

# 5. 验证结果
echo "验证升级结果..."
# 检查Agent版本等

# 6. 清理
kill $SERVER_PID
echo "测试完成"
```

## 8. 测试检查清单

### 8.1 升级前检查
- [ ] Agent正常运行
- [ ] 服务端正常运行
- [ ] 升级文件可访问
- [ ] 版本记录正确配置

### 8.2 升级过程检查
- [ ] 版本检查触发
- [ ] 文件下载成功
- [ ] 哈希验证通过
- [ ] 升级器启动
- [ ] 主程序退出
- [ ] 备份创建
- [ ] 文件替换
- [ ] 新版本启动

### 8.3 升级后检查
- [ ] 新版本正常运行
- [ ] 版本号更新
- [ ] 心跳恢复
- [ ] 任务分发正常
- [ ] 升级历史记录
- [ ] 临时文件清理

## 9. 故障排除

### 9.1 常见错误码
- **下载失败**: 检查URL和网络
- **哈希不匹配**: 文件损坏或配置错误
- **启动失败**: JAR文件无效或依赖缺失
- **权限不足**: 文件系统权限问题

### 9.2 调试技巧
1. 增加日志级别
2. 检查进程状态
3. 验证文件完整性
4. 测试网络连接

## 10. 总结

Agent自动升级功能支持多种测试场景，包括：
- 正常升级流程
- 任务保护机制
- 失败回滚机制
- 强制升级策略

通过本指南的测试步骤，可以全面验证升级功能的正确性和稳定性。