# LightScript Agent

LightScript 分布式脚本执行系统的客户端代理程序。

## 功能特性

- ✅ 自动注册到服务器
- ✅ 定期发送心跳保持连接
- ✅ 自动拉取并执行任务
- ✅ 支持多种脚本类型 (Bash, PowerShell, CMD)
- ✅ 实时日志上传
- ✅ 任务超时控制
- ✅ 优雅关闭处理

## 快速启动

### 方式一：直接运行（推荐）
```bash
# Windows - 直接运行已构建的jar
run-agent.bat [服务器地址] [注册令牌]

# Linux/macOS
java -jar target/agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar [服务器地址] [注册令牌]
```

### 方式二：重新构建并运行
```bash
# Windows - 重新构建后运行
start-agent.bat [服务器地址] [注册令牌]

# Linux/macOS
chmod +x start-agent.sh
./start-agent.sh [服务器地址] [注册令牌]
```

### 示例
```bash
# 使用默认配置启动
run-agent.bat

# 指定服务器地址
run-agent.bat http://192.168.1.100:8080

# 指定服务器地址和注册令牌
run-agent.bat http://192.168.1.100:8080 my-register-token
```

## 配置说明

### 默认配置
- **服务器地址**: http://localhost:8080
- **注册令牌**: dev-register-token
- **心跳间隔**: 30秒
- **任务拉取间隔**: 5秒
- **任务超时**: 300秒

### 环境要求
- JDK 1.8+
- Maven 3.6+
- 网络连接到LightScript服务器

## 日志文件

Agent运行时会在 `logs/` 目录下生成以下日志文件：

- **agent.log** - 主要日志，包含启动、心跳、任务拉取等信息
- **tasks.log** - 任务执行日志，包含脚本执行的详细信息

日志文件会自动按日期滚动，单个文件最大10MB，保留30天。

## 支持的脚本类型

### Windows
- **PowerShell** - 使用 `powershell -Command` 执行
- **CMD** - 使用 `cmd /c` 执行

### Linux/macOS
- **Bash** - 使用 `bash -c` 执行

## 目录结构

```
agent/
├── src/main/java/com/example/lightscript/agent/
│   ├── AgentMain.java          # 主程序入口
│   ├── AgentApi.java           # API通信接口
│   └── SimpleTaskRunner.java   # 任务执行器
├── src/main/resources/
│   └── logback.xml             # 日志配置
├── logs/                       # 日志文件目录
│   ├── agent.log              # 主日志
│   └── tasks.log              # 任务日志
├── start-agent.bat            # Windows启动脚本
├── start-agent.sh             # Linux/macOS启动脚本
├── pom.xml                    # Maven配置
└── README.md                  # 本文档
```

## 故障排除

### 常见问题

1. **无法连接到服务器**
   - 检查服务器地址是否正确
   - 确认服务器是否正在运行
   - 检查网络连接和防火墙设置

2. **注册失败**
   - 检查注册令牌是否正确
   - 确认服务器是否允许新的Agent注册

3. **任务执行失败**
   - 检查脚本语法是否正确
   - 确认Agent有执行脚本的权限
   - 查看 `logs/tasks.log` 获取详细错误信息

4. **构建失败**
   - 确认JDK版本是否为1.8+
   - 确认Maven版本是否为3.6+
   - 检查网络连接，确保能下载依赖

### 日志级别

可以通过修改 `src/main/resources/logback.xml` 调整日志级别：

- **DEBUG** - 详细调试信息
- **INFO** - 一般信息（默认）
- **WARN** - 警告信息
- **ERROR** - 错误信息

## 开发说明

### 编译
```bash
mvn clean compile
```

### 打包
```bash
mvn clean package
```

### 运行
```bash
java -jar target/agent-*.jar [服务器地址] [注册令牌]
```

## 安全注意事项

1. **注册令牌** - 请使用强密码作为注册令牌
2. **网络安全** - 建议在内网环境中使用
3. **脚本权限** - Agent会以当前用户权限执行脚本，请注意权限控制
4. **日志安全** - 日志文件可能包含敏感信息，请妥善保管
