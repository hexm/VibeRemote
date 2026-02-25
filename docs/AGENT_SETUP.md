# Agent 配置和启动指南

## 概述

LightScript Agent是运行在目标服务器上的客户端程序，负责接收和执行来自服务器的脚本任务。

## 前置要求

- Java 8 或更高版本
- 网络连接到LightScript服务器

## 快速启动

### 方式一：连接到阿里云生产环境（推荐）

```bash
./scripts/mac/start-agent-aliyun.sh
```

这个脚本会自动连接到阿里云服务器 (http://8.138.114.34:8080)

### 方式二：连接到本地开发环境

```bash
./scripts/mac/start-agent.sh
```

然后按提示输入：
- Server URL: `http://localhost:8080` (默认)
- Register Token: `dev-register-token` (默认)

### 方式三：手动指定参数

```bash
cd agent/target
java -jar agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar \
     http://8.138.114.34:8080 \
     dev-register-token
```

## 配置说明

### 服务器地址

根据部署环境选择：

- **本地开发**: `http://localhost:8080`
- **阿里云生产**: `http://8.138.114.34:8080`
- **自定义**: `http://your-server-ip:port`

### 注册令牌

默认令牌：`dev-register-token`

⚠️ 生产环境建议修改此令牌以提高安全性。

修改方法：
1. 修改服务器配置文件中的注册令牌
2. 启动Agent时使用相同的令牌

## Agent 功能

### 自动注册
- Agent启动时自动向服务器注册
- 注册失败会自动重试（指数退避策略）
- 获取唯一的Agent ID和Token

### 心跳检测
- 每30秒向服务器发送心跳
- 保持连接活跃状态
- 连续失败3次自动重新注册

### 任务执行
- 每5秒轮询服务器获取新任务
- 支持多任务并发执行（最多2个）
- 支持Shell、Bash、Python等脚本
- 任务超时控制（默认300秒）

### 单实例保护
- 同一台机器只能运行一个Agent实例
- 使用文件锁机制防止重复启动
- 锁文件位置：`~/.lightscript/.agent.lock`

### 自动重连
- 网络断开自动重连
- 认证失败自动重新注册
- 保证服务高可用

## 启动日志示例

```
========================================
LightScript Agent - 连接阿里云服务器
========================================

📋 Configuration:
   Server URL: http://8.138.114.34:8080
   Register Token: dev-register-token

🚀 Starting LightScript Agent...
   Press Ctrl+C to stop the agent
========================================

Starting LightScript Agent...
Server: http://8.138.114.34:8080
Register Token: dev-register-token
Registering agent...
Agent registered successfully!
Agent ID: 1
Agent Token: abc123...
Agent started. Waiting for tasks...
Sending heartbeat...
Heartbeat sent at Wed Feb 25 17:00:00 CST 2026
```

## 测试Agent连接

### 1. 启动Agent

```bash
./scripts/mac/start-agent-aliyun.sh
```

### 2. 登录Web界面

访问: http://8.138.114.34:3000

登录账号: admin / admin123

### 3. 查看Agent状态

进入"节点管理"页面，应该能看到刚注册的Agent：
- 节点名称：你的主机名
- 状态：在线（绿色）
- 操作系统：MACOS 或 LINUX
- 最后心跳时间：刚刚

### 4. 创建测试任务

1. 进入"任务管理"页面
2. 点击"创建任务"
3. 填写任务信息：
   - 任务名称：测试任务
   - 脚本语言：bash
   - 脚本内容：
     ```bash
     echo "Hello from LightScript Agent!"
     hostname
     date
     ```
   - 执行节点：选择你的Agent
4. 点击"创建"

### 5. 查看执行结果

- 任务列表中查看任务状态
- 点击"查看日志"查看执行输出
- Agent控制台也会显示任务执行信息

## 常见问题

### Q: Agent无法连接到服务器

A: 检查以下几点：
1. 服务器地址是否正确
2. 服务器是否正常运行
3. 网络是否可达：`curl http://8.138.114.34:8080`
4. 防火墙是否开放8080端口

### Q: 注册失败

A: 可能原因：
1. 注册令牌不正确
2. 服务器未启动
3. 网络连接问题

Agent会自动重试，请等待或检查服务器日志。

### Q: 提示"Another Agent instance is already running"

A: 同一台机器已有Agent在运行。

解决方法：
1. 停止现有Agent（Ctrl+C）
2. 或删除锁文件：`rm ~/.lightscript/.agent.lock`

### Q: 任务执行失败

A: 检查：
1. 脚本语法是否正确
2. 执行权限是否足够
3. 依赖的命令是否存在
4. 查看Agent控制台的错误信息

### Q: Agent频繁重连

A: 可能原因：
1. 网络不稳定
2. 服务器重启
3. 认证令牌过期

Agent会自动处理，无需手动干预。

## 停止Agent

按 `Ctrl+C` 优雅停止Agent：
- 完成当前任务
- 释放资源
- 释放实例锁

## 生产环境部署

### 使用systemd管理（Linux）

创建服务文件 `/etc/systemd/system/lightscript-agent.service`:

```ini
[Unit]
Description=LightScript Agent
After=network.target

[Service]
Type=simple
User=lightscript
WorkingDirectory=/opt/lightscript/agent
ExecStart=/usr/bin/java -jar agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar http://server-ip:8080 your-token
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启动服务：
```bash
sudo systemctl enable lightscript-agent
sudo systemctl start lightscript-agent
sudo systemctl status lightscript-agent
```

### 使用launchd管理（macOS）

创建plist文件 `~/Library/LaunchAgents/com.lightscript.agent.plist`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.lightscript.agent</string>
    <key>ProgramArguments</key>
    <array>
        <string>/usr/bin/java</string>
        <string>-jar</string>
        <string>/path/to/agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar</string>
        <string>http://8.138.114.34:8080</string>
        <string>dev-register-token</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
</dict>
</plist>
```

加载服务：
```bash
launchctl load ~/Library/LaunchAgents/com.lightscript.agent.plist
```

## 相关文档

- [部署指南](./DEPLOYMENT_ALIYUN.md)
- [快速开始](./QUICK_START.md)
- [项目结构](./PROJECT_STRUCTURE.md)
