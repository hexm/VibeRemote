# LightScript 脚本管理指南

本文档说明各项目的启动和管理脚本的位置和用法。

## 📁 脚本组织结构

```
LightScript/
├── server/
│   ├── scripts/           # 服务器启动脚本
│   └── nginx/            # Nginx配置文件
├── web/scripts/          # 前端脚本  
├── portal/scripts/       # 门户网站脚本
├── agent/                # Agent脚本（直接在根目录）
├── verify-deployment.sh  # 全局部署验证脚本
└── SCRIPTS.md           # 本文档
```

## 🚀 快速重启

### 一键重启本地环境

**脚本**: `./restart-local-env.sh`

**功能**:
- 停止所有本地服务（服务器、前端、门户、Agent）
- 重新编译打包所有组件
- 启动所有服务（使用MySQL数据库）
- Agent在localtest目录下运行
- 验证所有服务状态

**使用场景**:
- 代码修改后需要完整重启
- 开发环境出现问题需要重置
- 新拉取代码后的环境初始化

**执行时间**: 约2-3分钟（取决于编译速度）

## 🚀 启动脚本

### 1. 服务器端 (Spring Boot)

**位置**: `server/scripts/`

- `start-with-mysql-enhanced.sh` - 使用MySQL数据库启动服务器（推荐）
- `start-with-mysql.sh` - 简化版MySQL启动脚本
- `start-server.sh` - 基础启动脚本

**使用方法**:
```bash
# 进入server目录
cd server

# 启动服务器（MySQL）
./scripts/start-with-mysql-enhanced.sh

# 或者使用相对路径
server/scripts/start-with-mysql-enhanced.sh
```

**访问地址**: http://localhost:8080

### 2. 前端管理界面 (React + Vite)

**位置**: `web/scripts/`

- `start-web.sh` - 前端开发服务器启动脚本

**使用方法**:
```bash
# 进入web目录
cd web

# 启动前端服务器
./scripts/start-web.sh

# 或者使用相对路径
web/scripts/start-web.sh
```

**访问地址**: http://localhost:3001

### 3. 门户网站 (静态页面)

**位置**: `portal/scripts/`

- `start-portal.sh` - 门户网站启动脚本
- `dev-server.sh` - 开发服务器脚本
- `deploy.sh` - 部署脚本

**使用方法**:
```bash
# 进入portal目录
cd portal

# 启动门户网站
./scripts/start-portal.sh

# 或者使用相对路径
portal/scripts/start-portal.sh
```

**访问地址**: http://localhost:8002

### 4. Agent (Java应用)

**位置**: `agent/` (直接在agent根目录)

- `start-agent.sh` - Agent启动脚本
- `stop-agent.sh` - Agent停止脚本
- `build-release.sh` - 构建发布包脚本

**使用方法**:
```bash
# 进入agent目录
cd agent

# 启动Agent
./start-agent.sh

# 停止Agent
./stop-agent.sh

# 或者使用相对路径
agent/start-agent.sh
```

## 🔧 管理脚本

### 服务器端管理

**位置**: `server/scripts/`

- `configure-mysql-aliyun.sh` - 配置阿里云MySQL
- `deploy-to-aliyun.sh` - 部署到阿里云
- `migrate-to-mysql.sh` - 数据库迁移
- `setup-mysql-aliyun.sh` - 设置阿里云MySQL

### 门户网站管理

**位置**: `portal/scripts/`

- `deploy.sh` - 门户网站部署脚本
- `deploy-portal.sh` - 门户网站完整部署脚本

### Nginx配置

**位置**: `server/nginx/`

- `lightscript.conf` - 生产环境Nginx配置文件
- `README.md` - Nginx配置说明文档

### Agent管理

**位置**: `agent/`

- `start-agent.sh` - Agent启动脚本
- `stop-agent.sh` - Agent停止脚本
- `uninstall-agent.sh` - Agent卸载脚本
- `build-release.sh` - 构建发布包脚本

**Agent部署**: `agent/scripts/`

- `deploy-agent-packages.sh` - **专门上传Agent安装包到阿里云**

**本地测试**: `agent/localtest/`

- `start-localtest-agent.sh` - 本地测试Agent启动脚本
- `agent.properties` - 本地测试配置文件

### 全局脚本

**位置**: 根目录

- `restart-local-env.sh` - 完整本地环境重启脚本
- `verify-deployment.sh` - 部署验证脚本

## 🌍 环境变量

各脚本支持以下环境变量：

- `LIGHTSCRIPT_PORTAL_PORT` - 门户网站端口（默认8002）
- `LIGHTSCRIPT_JVM_OPTS` - Agent JVM参数（默认-Xmx512m -Xms128m）

## 📝 使用建议

1. **开发环境启动顺序**:
   ```bash
   # 方式1: 手动逐个启动
   # 1. 启动服务器
   server/scripts/start-with-mysql-enhanced.sh
   
   # 2. 启动前端
   web/scripts/start-web.sh
   
   # 3. 启动门户（可选）
   portal/scripts/start-portal.sh
   
   # 4. 启动Agent（本地测试）
   agent/localtest/start-localtest-agent.sh
   
   # 方式2: 一键重启（推荐）
   ./restart-local-env.sh
   ```

2. **生产环境部署**:
   ```bash
   # 1. 配置Nginx（参考server/nginx/README.md）
   sudo cp server/nginx/lightscript.conf /etc/nginx/sites-available/
   
   # 2. 部署后端和前端
   server/scripts/deploy-to-aliyun.sh
   
   # 3. 部署门户网站
   portal/scripts/deploy-portal.sh
   
   # 4. 上传Agent安装包（仅在Agent版本更新时需要）
   agent/scripts/deploy-agent-packages.sh
   
   # 5. 验证部署
   ./verify-deployment.sh
   ```

2. **脚本权限**: 所有脚本都已设置执行权限，可直接运行

3. **Nginx配置**: 生产环境的Nginx配置文件位于`server/nginx/`，包含详细的部署说明

4. **日志位置**: 
   - 服务器日志: `server/logs/`
   - Agent日志: `agent/logs/`
   - 前端日志: 控制台输出
   - 门户日志: 控制台输出

4. **停止服务**: 
   - 服务器: Ctrl+C 或 `pkill -f spring-boot:run`
   - 前端: Ctrl+C
   - 门户: Ctrl+C  
   - Agent: `agent/stop-agent.sh` 或 `agent/uninstall-agent.sh`（完全卸载）

5. **部署验证**: 使用`./verify-deployment.sh`验证整个系统的部署状态

## ⚠️ 注意事项

- 所有脚本都设计为从各自项目目录运行
- 脚本会自动检测依赖环境（Java、Node.js、Python等）
- 建议按照上述启动顺序启动各服务
- Agent需要服务器先启动才能正常注册