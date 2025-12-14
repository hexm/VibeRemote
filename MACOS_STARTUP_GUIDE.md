# LightScript macOS 启动指南

## 🎉 项目已成功迁移到 macOS！

LightScript 项目已经成功从 Windows 迁移到 macOS，所有 Java 8 兼容性问题已解决，项目可以正常运行。

## ✅ 完成的工作

### 1. Java 8 兼容性修复
- **Server 模块**: 修复了所有 Java 11+ 特性，替换为 Java 8 兼容的实现
  - 替换 `Map.of()` 为 `new HashMap<>()`
  - 替换 `List.of()` 为 `new ArrayList<>()`
  - 替换 `Files.writeString()` 为 `Files.write()`
  - 修复泛型类型问题

- **Agent 模块**: 完全重写网络通信层
  - 将 Java 11 HttpClient 替换为 Apache HttpClient 4.5.14
  - 添加 commons-logging 依赖
  - 修复类引用问题 (TaskRunner -> SimpleTaskRunner)

### 2. macOS 脚本转换
- 将所有 Windows `.bat` 脚本转换为 macOS `.sh` 脚本
- 脚本位置: `LightScript/scripts/mac/`
- 添加了额外的便利脚本: `start-all.sh`, `stop-all.sh`, `install-dependencies.sh`

### 3. Maven 环境配置
- 配置了 IDEA 内置 Maven 的环境变量
- Maven 3.9.9 + Java 1.8.0_472 (Temurin) 正常工作

### 4. 构建验证
- ✅ 项目完整构建成功: `mvn clean package -DskipTests`
- ✅ Server JAR: `server/target/server-0.1.0-SNAPSHOT.jar`
- ✅ Agent JAR: `agent/target/agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar`

### 5. 运行测试
- ✅ **Server**: 成功启动在 http://localhost:8080
- ✅ **Agent**: 成功注册并与服务器通信
- ✅ **Web**: 成功启动在 http://localhost:3000
- ✅ **数据库**: H2 内存数据库正常工作
- ✅ **用户账户**: admin/admin123, user/user123

## 🚀 快速启动

### 方式一: 使用便利脚本 (推荐)
```bash
cd LightScript

# 启动所有服务 (Server + Web)
./scripts/mac/start-all.sh

# 在另一个终端启动 Agent
./scripts/mac/start-agent.sh
```

### 方式二: 分别启动各个组件

#### 1. 启动 Server (后端服务)
```bash
cd LightScript
./scripts/mac/start-server.sh
```
- 访问: http://localhost:8080
- 登录: admin/admin123 或 user/user123
- H2 控制台: http://localhost:8080/h2-console

#### 2. 启动 Agent (任务执行器)
```bash
cd LightScript
./scripts/mac/start-agent.sh
```
- 默认连接到 http://localhost:8080
- 使用默认注册令牌: dev-register-token

#### 3. 启动 Web (前端界面)
```bash
cd LightScript
./scripts/mac/start-web.sh
```
- 访问: http://localhost:3000

## 📋 系统要求

### 已验证环境
- ✅ **操作系统**: macOS (darwin)
- ✅ **Java**: 1.8.0_472 (Temurin)
- ✅ **Maven**: 3.9.9 (IDEA 内置)
- ✅ **Shell**: zsh

### 必需软件
- Java 8+ (已安装)
- Maven (已配置)
- Python 3 (用于 Web 服务器，系统自带)

## 🔧 故障排除

### 如果遇到权限问题
```bash
chmod +x LightScript/scripts/mac/*.sh
```

### 如果需要重新构建
```bash
cd LightScript
mvn clean package -DskipTests
```

### 如果 Agent 连接失败
1. 确保 Server 已启动 (http://localhost:8080)
2. 检查防火墙设置
3. 查看 Agent 日志输出

### 如果 Web 界面无法访问
1. 确保 Server 在 8080 端口运行
2. 确保 Web 服务器在 3000 端口运行
3. 检查浏览器控制台错误

## 📁 重要文件位置

```
LightScript/
├── scripts/mac/           # macOS 启动脚本
├── server/target/         # Server JAR 文件
├── agent/target/          # Agent JAR 文件
├── web/                   # Web 前端文件
└── MACOS_STARTUP_GUIDE.md # 本指南
```

## 🎯 下一步

1. **测试功能**: 通过 Web 界面创建和执行脚本任务
2. **配置调优**: 根据需要调整 JVM 参数和数据库配置
3. **生产部署**: 参考 `DEPLOYMENT_GUIDE.md` 进行生产环境部署

## 📞 技术支持

如果遇到问题，请检查:
1. Java 版本: `java -version`
2. Maven 版本: `mvn -version`
3. 端口占用: `lsof -i :8080` 和 `lsof -i :3000`
4. 日志输出: 查看启动脚本的控制台输出

---

**恭喜！LightScript 已成功在 macOS 上运行！** 🎉