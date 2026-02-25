# LightScript 阿里云部署指南

## 📋 前置要求

### 本地环境
- macOS 系统
- Java 11+
- Maven 3.6+
- Node.js 16+
- SSH 客户端

### 服务器环境
- 阿里云ECS服务器
- IP地址: 8.138.114.34
- 操作系统: CentOS 7+ / Ubuntu 18.04+
- 开放端口: 8080 (后端), 3000 (前端)

## 🚀 快速部署

### 第一步：配置SSH免密登录（仅需一次）

```bash
./scripts/mac/setup-ssh-key.sh
```

这个脚本会：
1. 生成SSH密钥对（如果不存在）
2. 将公钥复制到服务器
3. 测试SSH连接

**注意**: 这一步需要输入一次服务器密码

### 第二步：一键部署

```bash
./scripts/mac/deploy-to-aliyun.sh
```

这个脚本会自动完成：
1. ✅ 本地构建后端（Maven）
2. ✅ 本地构建前端（npm build）
3. ✅ 创建部署包
4. ✅ 上传到服务器
5. ✅ 配置服务器环境
6. ✅ 启动服务

## 📍 访问地址

部署完成后，可以通过以下地址访问：

- **前端界面**: http://8.138.114.34:3000
- **后端API**: http://8.138.114.34:8080
- **API文档**: http://8.138.114.34:8080/swagger-ui.html

## 🔐 默认账号

- **管理员**: admin / admin123
- **普通用户**: user / user123

## 🛠️ 服务管理

### 查看服务状态

```bash
# 查看后端日志
ssh root@8.138.114.34 'tail -f /opt/lightscript/backend/backend.log'

# 查看前端日志
ssh root@8.138.114.34 'tail -f /opt/lightscript/frontend/frontend.log'

# 查看进程
ssh root@8.138.114.34 'ps aux | grep lightscript'
```

### 重启服务

```bash
ssh root@8.138.114.34 '/opt/lightscript/scripts/restart-all.sh'
```

### 停止服务

```bash
ssh root@8.138.114.34 '/opt/lightscript/scripts/stop-all.sh'
```

### 启动服务

```bash
# 启动后端
ssh root@8.138.114.34 '/opt/lightscript/scripts/start-backend.sh'

# 启动前端
ssh root@8.138.114.34 '/opt/lightscript/scripts/start-frontend.sh'
```

## 📂 服务器目录结构

```
/opt/lightscript/
├── backend/
│   ├── server.jar              # 后端JAR包
│   ├── application-prod.yml    # 生产环境配置
│   ├── backend.log            # 后端日志
│   └── backend.pid            # 后端进程ID
├── frontend/
│   ├── index.html             # 前端入口
│   ├── assets/                # 前端资源
│   ├── frontend.log           # 前端日志
│   └── frontend.pid           # 前端进程ID
├── data/
│   └── lightscript.mv.db      # H2数据库文件
├── logs/
│   └── lightscript-server.log # 应用日志
└── scripts/
    ├── start-backend.sh       # 启动后端
    ├── start-frontend.sh      # 启动前端
    ├── stop-all.sh            # 停止所有服务
    └── restart-all.sh         # 重启所有服务
```

## 🔧 配置说明

### 修改服务器地址

如果需要更改服务器地址，编辑以下文件：

1. `scripts/mac/setup-ssh-key.sh`
2. `scripts/mac/deploy-to-aliyun.sh`
3. `server/src/main/resources/application-prod.yml`

将 `8.138.114.34` 替换为新的IP地址。

### 修改端口

编辑 `scripts/mac/deploy-to-aliyun.sh`：

```bash
BACKEND_PORT="8080"   # 后端端口
FRONTEND_PORT="3000"  # 前端端口
```

同时修改 `application-prod.yml` 中的端口配置。

### 修改用户名

如果服务器用户不是 root，编辑脚本中的：

```bash
SERVER_USER="root"  # 改为实际用户名
```

## 🔒 安全建议

1. **修改JWT密钥**: 编辑 `application-prod.yml` 中的 `jwt.secret`
2. **修改默认密码**: 首次登录后立即修改默认账号密码
3. **配置HTTPS**: 建议使用Nginx反向代理并配置SSL证书
4. **限制访问**: 配置防火墙规则，只允许必要的IP访问

## 🐛 故障排查

### 服务无法启动

```bash
# 检查Java是否安装
ssh root@8.138.114.34 'java -version'

# 检查端口是否被占用
ssh root@8.138.114.34 'netstat -tlnp | grep 8080'
ssh root@8.138.114.34 'netstat -tlnp | grep 3000'

# 查看详细日志
ssh root@8.138.114.34 'cat /opt/lightscript/backend/backend.log'
```

### 无法访问服务

1. 检查阿里云安全组规则，确保开放了 8080 和 3000 端口
2. 检查服务器防火墙配置
3. 确认服务是否正常运行

### 数据库问题

```bash
# 检查数据库文件
ssh root@8.138.114.34 'ls -lh /opt/lightscript/data/'

# 重置数据库（会清空所有数据）
ssh root@8.138.114.34 'rm -f /opt/lightscript/data/lightscript.mv.db'
ssh root@8.138.114.34 '/opt/lightscript/scripts/restart-all.sh'
```

## 📊 性能优化

### JVM参数调优

编辑 `scripts/start-backend.sh`，添加JVM参数：

```bash
java -Xms512m -Xmx1024m -jar server.jar --spring.profiles.active=prod
```

### 使用Nginx反向代理

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端
    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # 后端API
    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 🔄 更新部署

当代码有更新时，只需重新运行部署脚本：

```bash
./scripts/mac/deploy-to-aliyun.sh
```

脚本会自动：
1. 备份现有部署
2. 停止服务
3. 部署新版本
4. 启动服务

## 📞 技术支持

如有问题，请查看：
- 后端日志: `/opt/lightscript/backend/backend.log`
- 前端日志: `/opt/lightscript/frontend/frontend.log`
- 应用日志: `/opt/lightscript/logs/lightscript-server.log`

## 📝 更新日志

### v1.0.0 (2024-01-15)
- ✅ 初始版本
- ✅ 支持一键部署
- ✅ 自动化构建和上传
- ✅ 服务管理脚本
