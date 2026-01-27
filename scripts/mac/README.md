# LightScript macOS 启动脚本

这个目录包含了在 macOS 系统上运行 LightScript 项目的所有启动脚本。

## 📋 脚本列表

| 脚本名称 | 功能描述 |
|---------|---------|
| `build.sh` | 构建整个项目 |
| `quick-start.sh` | 快速启动服务器（开发模式） |
| `start-server.sh` | 启动后端服务器 |
| `start-agent.sh` | 启动客户端代理 |
| `start-web.sh` | 启动传统前端服务 (Vue 3) |
| `start-modern-web.sh` | 启动现代化前端服务 (React 18) ⭐ |
| `start-all.sh` | 一键启动所有服务（传统版本） |
| `start-all-modern.sh` | 一键启动所有服务（现代化版本） ⭐ |
| `stop-all.sh` | 停止所有服务 |
| `reset-agent-id.sh` | 重置 Agent ID |

## 🚀 快速开始

### 1. 系统要求

确保你的 macOS 系统已安装以下软件：

```bash
# 安装 Java (JDK 1.8+)
brew install openjdk@8

# 安装 Maven
brew install maven

# 安装 Node.js (用于前端服务，可选)
brew install node
npm install -g http-server

# 或者安装 Python (备选前端服务)
brew install python
```

### 2. 一键启动（推荐）

#### 现代化版本 ⭐
```bash
# 进入项目根目录
cd LightScript

# 给脚本添加执行权限
chmod +x scripts/mac/*.sh

# 一键启动现代化版本
./scripts/mac/start-all-modern.sh
```

这个命令会：
- 自动构建项目
- 启动后端服务器 (http://localhost:8080)
- 启动现代化前端服务 (http://localhost:3001) ⭐
- 显示访问地址和默认账号

#### 传统版本
```bash
# 一键启动传统版本
./scripts/mac/start-all.sh
```

这个命令会：
- 自动构建项目
- 启动后端服务器 (http://localhost:8080)
- 启动传统前端服务 (http://localhost:3000)
- 显示访问地址和默认账号

### 3. 分步启动

如果你想分步启动各个服务：

```bash
# 1. 构建项目
./scripts/mac/build.sh

# 2. 启动服务器
./scripts/mac/start-server.sh

# 3. 启动前端（新终端窗口）
# 现代化版本（推荐）
./scripts/mac/start-modern-web.sh

# 或传统版本
./scripts/mac/start-web.sh

# 4. 启动客户端代理（新终端窗口）
./scripts/mac/start-agent.sh
```

### 4. 停止服务

```bash
# 停止所有后台服务
./scripts/mac/stop-all.sh
```

## 🔧 脚本详细说明

### build.sh
- 清理并构建整个项目
- 生成服务器和客户端的 JAR 文件
- 检查 Maven 环境

### start-server.sh
- 启动后端服务器
- 使用 H2 内存数据库（无需配置 MySQL）
- 自动构建（如果 JAR 文件不存在）
- 优化的 JVM 参数

### start-agent.sh
- 启动客户端代理
- 交互式配置服务器地址和注册令牌
- 支持环境变量配置
- 自动构建（如果 JAR 文件不存在）

### start-web.sh
- 启动前端服务
- 优先使用 http-server，备选 Python
- 自动 CORS 配置
- 端口 3000

### start-all.sh
- 一键启动所有服务
- 后台运行服务器和前端
- 自动检查依赖
- 保存进程 PID 用于后续停止

### stop-all.sh
- 停止所有后台服务
- 清理 PID 文件
- 可选删除日志文件

## 🌐 访问地址

启动成功后，你可以访问：

### 现代化版本 ⭐
- **现代化前端界面**: http://localhost:3001
- **后端 API**: http://localhost:8080
- **H2 数据库控制台**: http://localhost:8080/h2-console

### 传统版本
- **传统前端界面**: http://localhost:3000
- **后端 API**: http://localhost:8080
- **H2 数据库控制台**: http://localhost:8080/h2-console

### 🎨 版本对比
| 特性 | 传统版本 | 现代化版本 |
|------|----------|------------|
| **框架** | Vue 3 + Element Plus | React 18 + Ant Design 5 ⭐ |
| **样式** | 传统CSS | Tailwind CSS ⭐ |
| **设计** | 企业风格 | 现代渐变设计 ⭐ |
| **动画** | 基础 | 流畅动画效果 ⭐ |
| **响应式** | 基础支持 | 完全响应式 ⭐ |
| **性能** | 一般 | 高性能优化 ⭐ |

## 👤 默认账号

- **管理员**: `admin` / `admin123`
- **普通用户**: `user` / `user123`

## 🔍 故障排除

### 端口被占用
```bash
# 查看端口占用
lsof -i :8080
lsof -i :3000

# 杀死占用进程
kill -9 <PID>
```

### Java 版本问题
```bash
# 检查 Java 版本
java -version

# 如果版本不对，设置 JAVA_HOME
export JAVA_HOME=/usr/libexec/java_home -v 1.8
```

### Maven 构建失败
```bash
# 清理 Maven 缓存
mvn clean
rm -rf ~/.m2/repository

# 重新构建
./scripts/mac/build.sh
```

### 权限问题
```bash
# 给所有脚本添加执行权限
chmod +x scripts/mac/*.sh
```

## 📝 环境变量

你可以通过环境变量配置一些参数：

```bash
# Agent 注册令牌
export LIGHTSCRIPT_REGISTER_TOKEN="your-custom-token"

# Java 内存设置
export JAVA_OPTS="-Xmx1g -Xms512m"
```

## 🔄 开发模式

对于开发调试，推荐使用：

```bash
# 快速启动（仅服务器，使用 H2 数据库）
./scripts/mac/quick-start.sh
```

这个模式下：
- 使用内存数据库，重启后数据会丢失
- 适合快速测试和开发
- 不需要配置 MySQL

## 📚 更多信息

- 项目文档: `../README.md`
- 部署指南: `../DEPLOYMENT_GUIDE.md`
- 快速开始: `../QUICK_START.md`