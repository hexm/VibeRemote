# LightScript

LightScript 是一个分布式脚本执行管理系统，支持在多台服务器上批量执行脚本任务。

## 🚀 项目特性

- ✅ **分布式架构** - 支持多Agent管理
- ✅ **多脚本支持** - Bash、PowerShell、CMD
- ✅ **实时监控** - 任务执行状态和日志实时查看
- ✅ **Web管理界面** - 基于Vue.js的现代化界面
- ✅ **RESTful API** - 完整的API接口
- ✅ **高可用性** - 心跳检测和自动重连

## 📁 项目结构

```
lightScript/
├── server/                    # 服务端 (Spring Boot)
│   ├── src/main/java/        # Java源码
│   ├── src/main/resources/   # 配置文件
│   └── pom.xml              # Maven配置
├── agent/                    # 客户端代理
│   ├── src/main/java/       # Java源码
│   ├── logs/                # 日志文件
│   ├── run-agent.bat        # 快速启动脚本
│   └── pom.xml             # Maven配置
├── web/                     # Web前端 (Vue.js)
│   ├── index.html          # 主页
│   ├── login.html          # 登录页
│   ├── dashboard.html      # 仪表盘
│   ├── agents.html         # Agent管理
│   ├── tasks.html          # 任务管理
│   ├── scripts.html        # 脚本管理
│   ├── js/                 # JavaScript文件
│   └── css/                # 样式文件
└── docs/                   # 文档
```

## 🚀 快速启动

### 1. 启动服务器
```bash
# 使用快速启动脚本
quick-start.bat

# 或手动启动
cd server
mvn clean package -DskipTests
java -jar target/server-*.jar --spring.profiles.active=dev
```

### 2. 启动Agent
```bash
cd agent
run-agent.bat

# 或指定服务器地址
run-agent.bat http://your-server:8080 your-register-token
```

### 3. 访问Web界面
- **前端页面**: 直接打开 `web/index.html` (推荐使用HTTP服务器)
- **API接口**: http://localhost:8080/api/*
- **数据库控制台**: http://localhost:8080/h2-console (开发环境)

**注意**: 项目采用前后端分离架构，前端文件在 `web/` 目录下独立部署。

## 🛠️ 技术栈

### 后端
- **Java**: JDK 1.8+
- **框架**: Spring Boot 2.7.x
- **数据库**: MySQL 8.0 / H2 (开发)
- **安全**: Spring Security + JWT
- **构建**: Maven 3.6+

### 前端
- **框架**: Vue.js 3
- **UI组件**: Element Plus
- **HTTP客户端**: Axios
- **架构**: 多页面应用 (MPA)

### Agent
- **语言**: Java 8
- **HTTP客户端**: Java 11 HttpClient (向后兼容)
- **日志**: Logback
- **打包**: Maven Assembly Plugin

## 📖 详细文档

- [快速启动指南](QUICK_START.md)
- [服务器部署指南](server/README.md)
- [Agent使用说明](agent/README.md)
- [Web界面说明](web/README.md)

## 🤝 贡献

欢迎提交Issue和Pull Request！

## 📄 许可证

MIT License

### 环境要求
- JDK 1.8+
- Maven 3.6+
- MySQL 8.0+

### 数据库配置
1. 创建数据库：
```sql
CREATE DATABASE lightscript CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 修改配置文件 `server/src/main/resources/application.yml`：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/lightscript?useSSL=false&serverTimezone=UTC
    username: your_username
    password: your_password
```

### 构建项目
```bash
mvn clean package -DskipTests
```

### 启动服务器
```bash
java -jar server/target/server-*.jar
```

服务器启动后访问: `http://localhost:8080`

**默认用户账号:**
- 管理员: `admin` / `admin123`
- 普通用户: `user` / `user123`

### 启动客户端
```bash
# Windows PowerShell
$env:LS_SERVER="http://localhost:8080"
$env:LS_REGISTER_TOKEN="dev-register-token"
java -jar agent/target/agent-*-jar-with-dependencies.jar

# Linux/macOS
LS_SERVER=http://localhost:8080 LS_REGISTER_TOKEN=dev-register-token \
java -jar agent/target/agent-*-jar-with-dependencies.jar
```

## 使用指南

### Web管理界面
1. **登录系统**: 使用默认账号登录管理界面
2. **查看仪表盘**: 查看系统概览和统计信息
3. **管理客户端**: 查看在线客户端列表和状态
4. **创建任务**: 为单个或多个客户端创建脚本任务
5. **监控执行**: 实时查看任务执行状态和日志

### API接口
- **Agent注册**: `POST /api/agent/register`
- **心跳检测**: `POST /api/agent/heartbeat`
- **拉取任务**: `GET /api/agent/tasks/pull`
- **上报日志**: `POST /api/agent/tasks/{taskId}/log`
- **完成任务**: `POST /api/agent/tasks/{taskId}/finish`

### 批量脚本执行
1. 在Web界面选择"任务管理"
2. 点击"批量下发脚本"
3. 选择目标客户端
4. 输入脚本内容和参数
5. 提交执行并监控结果

## 项目结构
```
lightScript/
├── server/                 # 服务器端代码
│   ├── src/main/java/     # Java源码
│   │   ├── config/        # 配置类
│   │   ├── entity/        # 数据库实体
│   │   ├── repository/    # 数据访问层
│   │   ├── service/       # 业务逻辑层
│   │   ├── web/          # 控制器层
│   │   └── security/     # 安全配置
│   └── src/main/resources/
│       ├── static/        # 前端静态文件
│       └── application.yml # 配置文件
├── agent/                 # 客户端代码
│   └── src/main/java/     # Agent源码
├── demo/                  # 参考代码
└── pom.xml               # 父级Maven配置
```

## 安全特性
- JWT令牌认证
- 密码加密存储
- Agent令牌验证
- SQL注入防护
- XSS攻击防护

## 性能优化
- Agent轻量级设计
- 数据库连接池
- 异步任务处理
- 内存使用优化
- 心跳间隔优化

## 部署建议
- 使用反向代理(Nginx)
- 配置HTTPS证书
- 设置数据库备份
- 监控系统资源
- 日志轮转配置

## 开发计划
- [ ] 脚本模板管理
- [ ] 任务调度功能
- [ ] 文件传输支持
- [ ] 集群部署支持
- [ ] 监控告警功能

## 许可证
MIT License

## 贡献指南
欢迎提交Issue和Pull Request来改进项目。