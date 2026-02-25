# LightScript 快速启动指南

## 🚀 最小成本快速启动

### 环境要求
- JDK 1.8+
- Maven 3.6+

### 一键启动服务器

**Windows:**
```bash
quick-start.bat
```

**Linux/macOS:**
```bash
chmod +x quick-start.sh
./quick-start.sh
```

### 访问地址
- **Web管理界面**: http://localhost:8080
- **H2数据库控制台**: http://localhost:8080/h2-console

### 特性
- ✅ 使用内存数据库 (H2)，无需配置MySQL
- ✅ 自动创建表结构
- ✅ 简化安全配置，便于开发调试
- ✅ 包含完整的REST API
- ✅ 支持Agent注册和任务管理

### 默认配置
- 端口: 8080
- 数据库: H2内存数据库
- 注册令牌: `dev-register-token`
- JWT密钥: `lightscript-dev-secret-key-for-testing-only`

### API测试
```bash
# 健康检查
curl http://localhost:8080/actuator/health

# Agent注册 (示例)
curl -X POST http://localhost:8080/api/agent/register \
  -H "Content-Type: application/json" \
  -d '{
    "registerToken": "dev-register-token",
    "hostname": "test-host",
    "osType": "LINUX",
    "ip": "127.0.0.1"
  }'
```

### 数据库查看
1. 访问 http://localhost:8080/h2-console
2. 使用以下连接信息:
   - JDBC URL: `jdbc:h2:mem:lightscript`
   - 用户名: `sa`
   - 密码: (留空)

### 生产环境配置
要切换到MySQL生产环境，请:
1. 修改 `application.yml` 中的数据库配置
2. 启用完整的安全配置
3. 使用 `--spring.profiles.active=prod` 启动

### 故障排除
- 如果端口8080被占用，修改 `application-dev.yml` 中的端口
- 如果构建失败，检查JDK和Maven版本
- 查看控制台日志获取详细错误信息
