# VibeRemote

VibeRemote 是一个轻量级的分布式脚本执行管理平台，支持在多台远程主机上批量下发和执行脚本任务，并实时查看执行日志。

[![GitHub Stars](https://img.shields.io/github/stars/hexm/VibeRemote?style=flat-square&logo=github)](https://github.com/hexm/VibeRemote)
[![GitHub Downloads](https://img.shields.io/github/downloads/hexm/VibeRemote/total?style=flat-square&logo=github)](https://github.com/hexm/VibeRemote/releases)
[![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](LICENSE)

## 功能特性

- 多 Agent 管理，支持 Windows / macOS / Linux
- 脚本加密存储与传输
- 任务下发、实时日志查看
- Agent 心跳检测与自动重连
- Agent 自动升级
- 屏幕截图监控（可选）
- Web 管理后台（React + Vite）

## 项目结构

```
VibeRemote/
├── server/          # 服务端 (Spring Boot 2.7 / Java 8)
├── agent/           # Agent 客户端 (Java 8)
├── web/             # 管理前端 (React + Vite)
└── portal/          # 门户首页 (静态页面)
```

## 技术栈

| 层级 | 技术 |
|------|------|
| 服务端 | Java 8, Spring Boot 2.7, Spring Security + JWT, MySQL 8 |
| Agent | Java 8, Logback, Maven Assembly |
| 前端 | React 18, Vite, Axios |

## 快速开始

### 环境要求

- JDK 8+
- Maven 3.6+
- MySQL 8.0+
- Node.js 18+（前端构建）

### 1. 初始化数据库

```sql
CREATE DATABASE lightscript CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 配置服务端

编辑 `server/src/main/resources/application-dev.yml`，填写数据库连接信息和加密密钥：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/lightscript?useSSL=false&serverTimezone=UTC
    username: your_username
    password: your_password

encryption:
  key: your-32-char-hex-key
```

### 3. 启动服务端

```bash
cd server
mvn clean package -DskipTests
java -jar target/server-*.jar --spring.profiles.active=dev
```

服务端默认监听 `http://localhost:8080`

默认账号：`admin` / `admin123`

### 4. 安装 Agent

在目标主机上执行一键安装脚本（需要指定服务端地址）：

**macOS / Linux**
```bash
curl -fsSL http://your-server/scripts/install.sh | bash -s -- --server=http://your-server:8080
```

**Windows**（PowerShell）
```powershell
irm http://your-server/scripts/install.ps1 | iex
```

### 5. 构建前端

```bash
cd web
npm install
npm run build
```

将 `web/dist` 目录部署到 Web 服务器即可。

## Agent 管理

| 操作 | macOS/Linux | Windows |
|------|-------------|---------|
| 启动 | `start-agent.sh` | `start-agent.bat` |
| 停止 | `stop-agent.sh` | `stop-agent.bat` |
| 卸载 | `uninstall.sh` | `uninstall.bat` |

Agent 支持开机自启（macOS 使用 launchd，Linux 使用 systemd，Windows 使用计划任务）。

## 许可证

[MIT](LICENSE)
