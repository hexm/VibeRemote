# LightScript 测试指南

## 概述

本文档介绍如何测试 LightScript 的任务多目标支持功能。

## 自动化测试

### 快速测试（推荐）

如果服务已经在运行：

```bash
./scripts/mac/test-multi-target.sh
```

### 完整测试

自动启动所有服务并运行测试：

```bash
./scripts/mac/test-all.sh
```

这个脚本会：
1. 停止现有服务
2. 启动服务器
3. 启动前端（可选）
4. 运行所有自动化测试
5. 显示测试结果

## 测试覆盖范围

### 阶段 1: 环境检查
- ✅ 检查服务器是否运行
- ✅ 检查前端是否运行

### 阶段 2: 数据库迁移验证
- ✅ 验证 TaskExecution 表是否存在
- ✅ 验证表结构是否正确

### 阶段 3: API 端点测试
- ✅ 创建单代理任务
- ✅ 创建多代理任务
- ✅ 获取任务详情
- ✅ 获取任务执行实例列表
- ✅ 获取任务摘要
- ✅ 获取任务列表

### 阶段 4: Agent API 测试
- ✅ Agent 拉取任务
- ✅ Agent 确认任务（使用 executionId）
- ✅ Agent 上传日志（使用 executionId）
- ✅ Agent 完成任务（使用 executionId）

### 阶段 5: 任务管理功能测试
- ✅ 取消单个执行实例
- ✅ 重启任务（ALL 模式）
- ✅ 取消整个任务

### 阶段 6: 数据一致性验证
- ✅ 验证任务和执行实例的关联
- ✅ 验证聚合状态计算

## 测试结果

测试结果会保存在 `test-results/` 目录下，文件名格式为 `test-YYYYMMDD-HHMMSS.log`。

### 查看测试日志

```bash
# 查看最新的测试日志
ls -lt test-results/ | head -2
cat test-results/test-*.log
```

### 测试输出示例

```
==========================================
LightScript 多目标任务功能测试
测试时间: Wed Feb 26 18:50:00 CST 2026
==========================================

阶段 1: 环境检查

[TEST 1] 检查服务器是否运行
✅ 服务器 运行正常
✅ PASS

[TEST 2] 检查前端是否运行
✅ 前端 运行正常
✅ PASS

阶段 2: 数据库迁移验证

[TEST 3] 检查 TaskExecution 表是否存在
创建的测试任务ID: abc123...
✅ PASS

...

==========================================
测试总结
==========================================
总测试数: 20
通过: 20
失败: 0
✅ 所有测试通过！
==========================================
```

## 手动测试

如果需要手动测试，可以按照以下步骤：

### 1. 启动服务

```bash
# 启动服务器
./scripts/mac/start-server.sh

# 启动前端
./scripts/mac/start-modern-web.sh

# 启动 Agent（可选）
./scripts/mac/start-agent.sh
```

### 2. 访问 Web 界面

打开浏览器访问: http://localhost:3000

### 3. 测试功能

#### 3.1 创建单代理任务
1. 登录系统（admin/admin123）
2. 进入"任务管理"页面
3. 点击"创建任务"
4. 选择 1 个代理
5. 填写脚本内容：`echo "Hello Single Agent"`
6. 提交创建
7. 验证：任务列表显示"目标节点: 1"

#### 3.2 创建多代理任务
1. 点击"创建任务"
2. 选择 3 个代理
3. 填写脚本内容：`echo "Hello Multi Agent"`
4. 提交创建
5. 验证：任务列表显示"目标节点: 3"，"执行进度: 0/3"

#### 3.3 查看任务详情
1. 点击任务行
2. 验证：显示所有执行实例
3. 验证：显示统计卡片（目标节点、成功、失败、运行中）
4. 验证：显示整体进度条

#### 3.4 查看执行日志
1. 在任务详情中，点击某个执行实例的"查看日志"
2. 验证：显示该代理的日志内容
3. 验证：显示执行次数和 Agent 信息

#### 3.5 重启任务
1. 点击任务的"重启"按钮
2. 选择重启模式：
   - "重启所有"：为所有代理创建新执行实例
   - "仅重启失败"：只为失败的代理创建新执行实例
3. 验证：创建了新的执行实例
4. 验证：executionNumber 递增

#### 3.6 取消任务
1. 点击任务的"取消"按钮
2. 验证：所有未完成的执行实例状态变为 CANCELLED

## API 测试

### 使用 curl 测试

#### 创建多代理任务

```bash
curl -X POST "http://localhost:8080/api/web/tasks/create?agentIds=agent-1&agentIds=agent-2&agentIds=agent-3&taskName=test-task" \
  -H "Content-Type: application/json" \
  -d '{
    "scriptLang": "bash",
    "scriptContent": "echo Hello World",
    "timeoutSec": 60
  }'
```

#### 获取任务详情

```bash
curl "http://localhost:8080/api/web/tasks/{taskId}"
```

#### 获取执行实例列表

```bash
curl "http://localhost:8080/api/web/tasks/{taskId}/executions"
```

#### Agent 拉取任务

```bash
curl "http://localhost:8080/api/agent/tasks/pull?agentId=agent-1&agentToken=test-token&max=10"
```

#### Agent 确认任务

```bash
curl -X POST "http://localhost:8080/api/agent/tasks/executions/{executionId}/ack?agentId=agent-1&agentToken=test-token"
```

#### Agent 上传日志

```bash
curl -X POST "http://localhost:8080/api/agent/tasks/executions/{executionId}/log" \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "agent-1",
    "agentToken": "test-token",
    "executionId": 123,
    "seq": 1,
    "stream": "stdout",
    "data": "Test log message"
  }'
```

#### Agent 完成任务

```bash
curl -X POST "http://localhost:8080/api/agent/tasks/executions/{executionId}/finish" \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "agent-1",
    "agentToken": "test-token",
    "executionId": 123,
    "exitCode": 0,
    "status": "SUCCESS",
    "summary": "Task completed successfully"
  }'
```

## 故障排查

### 服务器无法启动

```bash
# 检查端口是否被占用
lsof -i :8080

# 查看服务器日志
tail -f logs/lightscript-server.log
```

### 数据库迁移失败

```bash
# 检查数据库连接
# 查看迁移日志
tail -f logs/lightscript-server.log | grep "Flyway"
```

### 测试失败

```bash
# 查看详细的测试日志
cat test-results/test-*.log

# 检查服务器日志
tail -f logs/lightscript-server.log

# 检查数据库状态
# 连接到 H2 数据库查看表结构
```

### Agent 无法连接

```bash
# 检查 Agent 配置
cat agent/agent.properties

# 查看 Agent 日志
tail -f logs/agent.log
```

## 性能测试

### 创建大量任务

```bash
# 创建 100 个多代理任务
for i in {1..100}; do
  curl -X POST "http://localhost:8080/api/web/tasks/create?agentIds=agent-1&agentIds=agent-2&agentIds=agent-3&taskName=perf-test-$i" \
    -H "Content-Type: application/json" \
    -d '{"scriptLang":"bash","scriptContent":"echo test","timeoutSec":60}'
  sleep 0.1
done
```

### 测试并发请求

```bash
# 使用 ab (Apache Bench) 测试
ab -n 1000 -c 10 http://localhost:8080/api/web/tasks?page=0&size=10
```

## 持续集成

### GitHub Actions 配置示例

```yaml
name: Test Multi-Target Support

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v2
    
    - name: Set up JDK 11
      uses: actions/setup-java@v2
      with:
        java-version: '11'
        distribution: 'adopt'
    
    - name: Build with Maven
      run: mvn clean package -DskipTests
    
    - name: Run tests
      run: ./scripts/mac/test-all.sh
    
    - name: Upload test results
      if: always()
      uses: actions/upload-artifact@v2
      with:
        name: test-results
        path: test-results/
```

## 总结

- ✅ 使用 `./scripts/mac/test-all.sh` 进行完整自动化测试
- ✅ 使用 `./scripts/mac/test-multi-target.sh` 快速测试（服务已运行）
- ✅ 查看 `test-results/` 目录获取详细测试日志
- ✅ 所有测试通过后即可部署到生产环境

---

**文档版本**: 1.0  
**最后更新**: 2026-02-26  
**维护者**: Kiro AI Assistant
