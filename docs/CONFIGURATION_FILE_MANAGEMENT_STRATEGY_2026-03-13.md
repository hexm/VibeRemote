# 配置文件管理策略

## 文档信息
- **创建时间**: 2026-03-13
- **版本**: 1.0
- **状态**: 策略制定
- **作者**: 系统架构师

## 1. 配置文件分类分析

### 1.1 Agent配置文件

#### `agent/src/main/resources/agent.properties`
```properties
# LightScript Agent 配置文件
server.url=http://localhost:8080
server.register.token=dev-register-token
agent.name=${hostname}
agent.labels=
heartbeat.interval=30000
heartbeat.system.info.interval=600000
heartbeat.max.failures=3
task.pull.max=10
task.pull.interval=5000
upgrade.backup.keep=1
upgrade.verify.timeout=15000
log.level=INFO
log.file.max.size=10MB
log.file.max.count=5
```

**分析**:
- ✅ **应该纳入源码管理** - 作为默认配置模板
- ⚠️ **包含敏感信息**: `server.register.token=dev-register-token`
- 🔧 **需要环境化**: `server.url`, `agent.name`

#### `agent/src/main/resources/version.properties`
```properties
# LightScript Agent Version Configuration
version=${project.version}
build.date=${maven.build.timestamp}
project.name=${project.name}
```

**分析**:
- ✅ **应该纳入源码管理** - Maven构建时自动替换变量
- 🔧 **构建时生成**: 使用Maven变量替换

### 1.2 Server配置文件

#### `server/src/main/resources/application.yml`
```yaml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:mysql://8.138.114.34:3306/lightscript?...
    username: lightscript
    password: lightscript123
  security:
    user:
      name: admin
      password: admin123
lightscript:
  jwt:
    secret: lightscript-jwt-secret-key-2024
  register:
    token: dev-register-token
```

**分析**:
- ❌ **不应该直接纳入源码管理** - 包含大量敏感信息
- 🔐 **敏感信息**: 数据库密码、JWT密钥、管理员密码
- 🔧 **需要模板化**: 创建模板文件，实际配置文件被忽略

### 1.3 其他配置文件

#### `agent/src/main/resources/logback.xml`
- ✅ **应该纳入源码管理** - 日志配置模板

#### Maven配置文件 (`pom.xml`)
- ✅ **已纳入源码管理** - 构建配置

## 2. 配置文件管理策略

### 2.1 模板化策略

#### 创建配置模板文件
```bash
# Agent配置模板
agent/src/main/resources/agent.properties.template

# Server配置模板  
server/src/main/resources/application.yml.template
```

#### 模板文件内容示例

**agent.properties.template**:
```properties
# LightScript Agent 配置模板
# 部署时复制为 agent.properties 并修改相应值

# 服务器配置
server.url=${LIGHTSCRIPT_SERVER_URL:http://localhost:8080}
server.register.token=${LIGHTSCRIPT_REGISTER_TOKEN:your-register-token}

# Agent配置
agent.name=${LIGHTSCRIPT_AGENT_NAME:${hostname}}
agent.labels=${LIGHTSCRIPT_AGENT_LABELS:}

# 心跳配置
heartbeat.interval=30000
heartbeat.system.info.interval=600000
heartbeat.max.failures=3

# 任务配置
task.pull.max=10
task.pull.interval=5000

# 升级配置
upgrade.backup.keep=1
upgrade.verify.timeout=15000

# 日志配置
log.level=${LIGHTSCRIPT_LOG_LEVEL:INFO}
log.file.max.size=10MB
log.file.max.count=5
```

**application.yml.template**:
```yaml
server:
  port: ${SERVER_PORT:8080}
  
spring:
  application:
    name: lightscript-server
  datasource:
    url: ${DATABASE_URL:jdbc:h2:file:./data/lightscript}
    username: ${DATABASE_USERNAME:sa}
    password: ${DATABASE_PASSWORD:}
    driver-class-name: ${DATABASE_DRIVER:org.h2.Driver}
  security:
    user:
      name: ${ADMIN_USERNAME:admin}
      password: ${ADMIN_PASSWORD:change-me}
      
lightscript:
  jwt:
    secret: ${JWT_SECRET:change-this-secret-key}
  register:
    token: ${REGISTER_TOKEN:change-this-token}
```

### 2.2 环境变量支持

#### Agent环境变量
```bash
# 必需的环境变量
LIGHTSCRIPT_SERVER_URL=http://your-server:8080
LIGHTSCRIPT_REGISTER_TOKEN=your-secure-token

# 可选的环境变量
LIGHTSCRIPT_AGENT_NAME=my-agent
LIGHTSCRIPT_AGENT_LABELS=env:prod,region:us-east
LIGHTSCRIPT_LOG_LEVEL=INFO
```

#### Server环境变量
```bash
# 数据库配置
DATABASE_URL=jdbc:mysql://localhost:3306/lightscript
DATABASE_USERNAME=lightscript
DATABASE_PASSWORD=secure-password

# 安全配置
ADMIN_USERNAME=admin
ADMIN_PASSWORD=secure-admin-password
JWT_SECRET=your-jwt-secret-key
REGISTER_TOKEN=your-register-token

# 服务器配置
SERVER_PORT=8080
```

### 2.3 .gitignore更新

#### 添加配置文件忽略规则
```gitignore
# 配置文件（包含敏感信息）
*.properties
!*.properties.template
!version.properties

# 应用配置
application.yml
application.yaml
!application.yml.template
!application.yaml.template

# 环境配置
.env
.env.local
.env.production

# Agent凭证
.agent-credentials
agent-credentials.json

# 数据库配置备份
*.h2.backup
```

## 3. 实施计划

### 3.1 立即操作

#### 1. 创建配置模板文件
```bash
# 备份当前配置
cp agent/src/main/resources/agent.properties agent/src/main/resources/agent.properties.template
cp server/src/main/resources/application.yml server/src/main/resources/application.yml.template

# 清理模板中的敏感信息
# 将具体值替换为环境变量或占位符
```

#### 2. 更新.gitignore
```bash
# 添加配置文件忽略规则
echo "" >> .gitignore
echo "# 配置文件管理" >> .gitignore
echo "*.properties" >> .gitignore
echo "!*.properties.template" >> .gitignore
echo "!version.properties" >> .gitignore
echo "application.yml" >> .gitignore
echo "!application.yml.template" >> .gitignore
echo ".env*" >> .gitignore
echo ".agent-credentials" >> .gitignore
```

#### 3. 从git中移除敏感配置
```bash
# 移除已提交的敏感配置文件
git rm --cached server/src/main/resources/application.yml
git rm --cached agent/localtest/.agent-credentials

# 保留模板文件
git add agent/src/main/resources/agent.properties.template
git add server/src/main/resources/application.yml.template
```

### 3.2 文档更新

#### 创建部署指南
```markdown
# 部署配置指南

## Agent配置
1. 复制配置模板: `cp agent.properties.template agent.properties`
2. 修改服务器地址和注册令牌
3. 设置Agent名称和标签

## Server配置  
1. 复制配置模板: `cp application.yml.template application.yml`
2. 配置数据库连接
3. 设置管理员账号和JWT密钥
4. 配置注册令牌
```

### 3.3 构建脚本更新

#### Maven配置增强
```xml
<!-- 在pom.xml中添加环境变量支持 -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-resources-plugin</artifactId>
    <configuration>
        <useDefaultDelimiters>false</useDefaultDelimiters>
        <delimiters>
            <delimiter>${*}</delimiter>
        </delimiters>
    </configuration>
</plugin>
```

## 4. 配置文件安全最佳实践

### 4.1 敏感信息处理

#### 不应该出现在源码中的信息
- 数据库密码
- JWT密钥
- 管理员密码
- 注册令牌
- 生产环境URL
- API密钥

#### 应该使用的替代方案
- 环境变量
- 配置文件模板
- 密钥管理服务
- Docker secrets
- Kubernetes ConfigMap/Secret

### 4.2 配置验证

#### 启动时配置检查
```java
// Agent启动时检查必需配置
@PostConstruct
public void validateConfig() {
    if (StringUtils.isEmpty(serverUrl)) {
        throw new IllegalStateException("server.url is required");
    }
    if (StringUtils.isEmpty(registerToken)) {
        throw new IllegalStateException("server.register.token is required");
    }
}
```

### 4.3 配置文档

#### README.md更新
```markdown
## 配置说明

### 快速开始
1. 复制配置模板文件
2. 设置必需的环境变量
3. 启动应用

### 环境变量
- `LIGHTSCRIPT_SERVER_URL`: 服务器地址
- `LIGHTSCRIPT_REGISTER_TOKEN`: 注册令牌
- `DATABASE_URL`: 数据库连接字符串
```

## 5. 总结

### 5.1 配置文件分类结果

#### ✅ 纳入源码管理
- `agent/src/main/resources/agent.properties.template` - Agent配置模板
- `agent/src/main/resources/version.properties` - 版本信息（Maven变量）
- `agent/src/main/resources/logback.xml` - 日志配置
- `server/src/main/resources/application.yml.template` - Server配置模板

#### ❌ 不纳入源码管理
- `agent/src/main/resources/agent.properties` - 实际配置（包含敏感信息）
- `server/src/main/resources/application.yml` - 实际配置（包含敏感信息）
- `.agent-credentials` - Agent凭证文件
- `.env*` - 环境变量文件

### 5.2 核心原则
1. **模板化**: 配置文件使用模板，实际配置不入库
2. **环境化**: 敏感信息通过环境变量传递
3. **文档化**: 提供清晰的配置说明和部署指南
4. **验证化**: 启动时验证必需配置项

### 5.3 安全收益
- 避免敏感信息泄露到源码仓库
- 支持多环境部署（开发、测试、生产）
- 简化配置管理和部署流程
- 提高配置安全性和可维护性

这样的配置管理策略既保证了源码的整洁性，又确保了部署的安全性和灵活性。