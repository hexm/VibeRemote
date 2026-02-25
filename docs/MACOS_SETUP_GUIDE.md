# LightScript macOS 设置指南

## 📋 项目分析

LightScript 是一个分布式脚本执行管理系统，包含以下主要功能：

### 🎯 核心功能
- **分布式架构**: 支持多 Agent 客户端管理
- **多脚本支持**: Bash、PowerShell、CMD 脚本执行
- **实时监控**: 任务执行状态和日志实时查看
- **Web 管理界面**: 基于 Vue.js 的现代化管理界面
- **RESTful API**: 完整的后端 API 接口
- **高可用性**: 心跳检测和自动重连机制

### 🏗️ 技术架构
- **后端**: Java 1.8 + Spring Boot 2.7.18 + MySQL/H2
- **前端**: Vue.js 3 + Element Plus + 原生 JavaScript
- **客户端**: Java 1.8 轻量级 Agent 程序

### 📁 项目结构
```
LightScript/
├── server/          # Spring Boot 后端服务
├── agent/           # Java 客户端代理程序
├── web/             # Vue.js 前端界面
├── scripts/mac/     # macOS 启动脚本 (新增)
└── docs/            # 项目文档
```

## 🚀 macOS 快速启动指南

### 第一步：安装系统依赖

```bash
# 进入项目目录
cd LightScript

# 运行依赖安装脚本
./scripts/mac/install-dependencies.sh
```

这个脚本会自动安装：
- Homebrew (如果未安装)
- OpenJDK 8
- Apache Maven
- Node.js + http-server (前端服务)
- Python (备选前端服务)

### 第二步：重新加载环境变量

```bash
# 重新加载 shell 配置
source ~/.zshrc

# 或者重新打开终端窗口
```

### 第三步：一键启动项目

```bash
# 一键启动所有服务
./scripts/mac/start-all.sh
```

这个命令会：
1. 自动构建项目
2. 启动后端服务器 (端口 8080)
3. 启动前端服务 (端口 3000)
4. 显示访问地址和默认账号

### 第四步：访问系统

启动成功后，打开浏览器访问：

- **前端管理界面**: http://localhost:3000
- **后端 API**: http://localhost:8080
- **H2 数据库控制台**: http://localhost:8080/h2-console

**默认登录账号**:
- 管理员: `admin` / `admin123`
- 普通用户: `user` / `user123`

### 第五步：启动客户端代理

在新的终端窗口中：

```bash
# 启动 Agent 客户端
./scripts/mac/start-agent.sh
```

按提示输入：
- 服务器地址: `http://localhost:8080` (默认)
- 注册令牌: `dev-register-token` (默认)

## 🔧 脚本功能说明

### 已转换的 Windows 脚本

| Windows 脚本 | macOS 脚本 | 功能说明 |
|-------------|-----------|---------|
| `quick-start.bat` | `quick-start.sh` | 快速启动服务器 |
| `start-server.bat` | `start-server.sh` | 启动后端服务 |
| `start-agent.bat` | `start-agent.sh` | 启动客户端代理 |
| `start-web.bat` | `start-web.sh` | 启动前端服务 |
| `build.bat` | `build.sh` | 构建项目 |
| `reset-agent-id.bat` | `reset-agent-id.sh` | 重置 Agent ID |

### 新增的 macOS 专用脚本

| 脚本名称 | 功能说明 |
|---------|---------|
| `install-dependencies.sh` | 自动安装系统依赖 |
| `start-all.sh` | 一键启动所有服务 |
| `stop-all.sh` | 停止所有后台服务 |

## 🛠️ 开发模式

### 分步启动（推荐开发调试）

```bash
# 1. 构建项目
./scripts/mac/build.sh

# 2. 启动服务器（终端1）
./scripts/mac/start-server.sh

# 3. 启动前端（终端2）
./scripts/mac/start-web.sh

# 4. 启动客户端（终端3）
./scripts/mac/start-agent.sh
```

### 快速开发模式

```bash
# 仅启动服务器（使用 H2 内存数据库）
./scripts/mac/quick-start.sh
```

## 🔍 系统使用指南

### 1. 登录系统
- 访问 http://localhost:3000
- 使用默认账号登录

### 2. 查看客户端
- 进入"客户端管理"页面
- 查看已连接的 Agent 状态

### 3. 执行脚本任务
- 进入"任务管理"页面
- 创建新任务，选择目标客户端
- 输入脚本内容并执行
- 实时查看执行日志

### 4. 批量操作
- 选择多个在线客户端
- 批量下发脚本任务
- 统一监控执行结果

## 🔧 故障排除

### 常见问题

**1. 端口被占用**
```bash
# 查看端口占用
lsof -i :8080
lsof -i :3000

# 杀死占用进程
kill -9 <PID>
```

**2. Java 版本问题**
```bash
# 检查 Java 版本
java -version

# 设置正确的 JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
```

**3. Maven 构建失败**
```bash
# 清理并重新构建
./scripts/mac/build.sh
```

**4. 权限问题**
```bash
# 给脚本添加执行权限
chmod +x scripts/mac/*.sh
```

### 停止服务

```bash
# 停止所有后台服务
./scripts/mac/stop-all.sh

# 或手动停止
# Ctrl+C (前台进程)
# kill <PID> (后台进程)
```

## 📚 更多功能

### 环境变量配置

```bash
# 自定义 Agent 注册令牌
export LIGHTSCRIPT_REGISTER_TOKEN="your-custom-token"

# 自定义 JVM 参数
export JAVA_OPTS="-Xmx1g -Xms512m"
```

### 生产环境部署

对于生产环境，建议：
1. 配置 MySQL 数据库
2. 使用 Nginx 反向代理
3. 配置 HTTPS 证书
4. 设置系统服务自启动

详细部署指南请参考：`DEPLOYMENT_GUIDE.md`

## 🎉 完成！

现在你已经成功将 LightScript 项目从 Windows 迁移到 macOS，并且可以正常运行所有功能。

**下一步建议**：
1. 熟悉 Web 管理界面
2. 测试脚本执行功能
3. 配置多个 Agent 客户端
4. 根据需要调整配置参数

如有问题，请查看项目文档或检查日志文件。