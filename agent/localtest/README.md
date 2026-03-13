# LightScript Agent 本地测试环境

这个目录包含了完整的Agent部署文件，用于本地测试和验证。

## 文件说明

- `agent.jar` - Agent主程序（最新版本）
- `upgrader.jar` - 升级器程序（最新版本）
- `start-agent.sh` - Unix/Linux/macOS启动脚本（简化版，不接收参数）
- `start-agent.bat` - Windows启动脚本（简化版，不接收参数）
- `agent.properties` - Agent配置文件（完整配置）
- `test-agent.sh` - 环境测试脚本
- `create-upgrade-test.sh` - 升级测试包创建脚本

## 配置说明

Agent现在使用配置文件管理所有设置，配置优先级：
1. 命令行参数（向后兼容）
2. 环境变量
3. 外部配置文件（`agent.properties`）
4. 内置配置文件
5. 默认值

### 环境变量配置（可选）

```bash
export LIGHTSCRIPT_SERVER_URL=http://localhost:8080
export LIGHTSCRIPT_REGISTER_TOKEN=dev-register-token
export LIGHTSCRIPT_AGENT_NAME=test-agent
export LIGHTSCRIPT_JVM_OPTS="-Xmx1g -Xms256m"
```

## 使用方法

### 1. 环境检查

首先运行环境测试脚本：

```bash
./test-agent.sh
```

### 2. 配置Agent

编辑 `agent.properties` 文件来修改配置：

```properties
# 服务器配置
server.url=http://localhost:8080
server.register.token=dev-register-token

# Agent配置
agent.name=test-agent
agent.labels=env=test,region=local

# 心跳配置
heartbeat.interval=30000
heartbeat.max.failures=3

# 日志配置
log.level=INFO
```

### 3. 启动Agent

Unix/Linux/macOS:
```bash
./start-agent.sh
```

Windows:
```cmd
start-agent.bat
```

**注意：** 启动脚本不再接收参数，所有配置通过配置文件或环境变量管理。

### 4. 验证运行

Agent启动后会：
1. 自动注册到服务器
2. 开始发送心跳
3. 拉取并执行任务
4. 检查版本更新

查看日志确认运行状态：
```bash
tail -f logs/agent.log
```

### 5. 升级测试

创建升级测试包：
```bash
./create-upgrade-test.sh 2.0.0
```

这会创建一个版本号为2.0.0的测试包，可以用于测试升级功能。

## 日志文件

所有日志现在统一保存在 `logs/` 目录下：

- `logs/agent.log` - 主要应用日志
- `logs/tasks.log` - 任务执行日志
- `logs/upgrade.log` - 升级过程日志
- `logs/upgrade-error.log` - 升级错误日志
- `logs/agent-startup.log` - 启动日志
- `logs/agent-startup-error.log` - 启动错误日志

## 目录结构

运行后会创建以下目录结构：

```
agent/localtest/
├── agent.jar              # 主程序
├── upgrader.jar           # 升级器
├── start-agent.sh         # 启动脚本
├── start-agent.bat        # Windows启动脚本
├── agent.properties       # 配置文件
├── logs/                  # 日志目录
│   ├── agent.log
│   ├── tasks.log
│   └── ...
├── backup/                # 备份目录
│   └── current/           # 当前备份
└── .agent-credentials     # Agent凭证（自动生成）
```

## 故障排除

如果Agent无法启动，请检查：

1. **Java环境**：确保Java 8+已安装
   ```bash
   java -version
   ```

2. **服务器连接**：确保服务器地址和端口正确
   ```bash
   curl http://localhost:8080/api/health
   ```

3. **配置文件**：检查 `agent.properties` 语法是否正确

4. **日志文件**：查看详细错误信息
   ```bash
   cat logs/agent.log
   cat logs/agent-startup-error.log
   ```

5. **权限问题**：确保启动脚本有执行权限
   ```bash
   chmod +x start-agent.sh
   ```

## 测试场景

### 基本功能测试
1. Agent注册和心跳
2. 任务拉取和执行
3. 日志记录

### 升级功能测试
1. 创建新版本测试包
2. 触发升级流程
3. 验证升级成功
4. 测试回滚机制

### 配置管理测试
1. 修改配置文件
2. 使用环境变量覆盖
3. 验证配置生效

## 清理环境

```bash
# 停止Agent进程
pkill -f "java -jar agent.jar"

# 清理锁文件
rm -f ~/.lightscript/.agent.lock

# 清理日志文件
rm -rf logs/

# 清理备份目录
rm -rf backup/
```

---

**注意**: 这是测试环境，请勿在生产环境中直接使用。