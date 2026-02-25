# LightScript 生产环境部署总结

## ✅ 部署完成

**部署时间**: 2026-02-25 16:42  
**服务器**: 阿里云ECS (8.138.114.34)  
**部署路径**: /opt/lightscript/

---

## 🌐 访问地址

### 方式一：通过80端口（推荐）
- **前端界面**: http://8.138.114.34
- **后端API**: http://8.138.114.34/api/

优点：统一域名，支持API代理

### 方式二：通过独立端口
- **前端界面**: http://8.138.114.34:3000
- **后端API**: http://8.138.114.34:8080

优点：前后端独立访问

---

## 🔐 默认账号

- **管理员**: admin / admin123
- **普通用户**: user / user123

⚠️ 首次登录后请立即修改密码

---

## 📊 服务状态

### 前端服务（Nginx）
- **状态**: ✅ 运行中
- **端口**: 80, 3000
- **版本**: nginx/1.20.1
- **配置**: /etc/nginx/conf.d/lightscript.conf

### 后端服务（Spring Boot）
- **状态**: ✅ 运行中
- **端口**: 8080
- **进程ID**: 48847
- **版本**: Spring Boot 2.7.18
- **Java版本**: 1.8.0_482
- **启动时间**: 7.761秒

### 数据库（H2）
- **状态**: ✅ 运行中
- **位置**: /opt/lightscript/data/lightscript.mv.db
- **模式**: 文件存储

---

## 🛠️ 服务管理

### 查看服务状态
```bash
# 查看Nginx状态
ssh root@8.138.114.34 'systemctl status nginx'

# 查看后端进程
ssh root@8.138.114.34 'ps aux | grep java'

# 查看端口监听
ssh root@8.138.114.34 'netstat -tlnp | grep -E "(80|3000|8080)"'
```

### 重启服务
```bash
# 重启所有服务
ssh root@8.138.114.34 '/opt/lightscript/scripts/restart-all.sh'

# 单独重启Nginx
ssh root@8.138.114.34 'systemctl restart nginx'

# 单独重启后端
ssh root@8.138.114.34 '/opt/lightscript/scripts/stop-all.sh && /opt/lightscript/scripts/start-backend.sh'
```

### 查看日志
```bash
# Nginx访问日志
ssh root@8.138.114.34 'tail -f /opt/lightscript/logs/nginx-access.log'

# Nginx错误日志
ssh root@8.138.114.34 'tail -f /opt/lightscript/logs/nginx-error.log'

# 后端日志
ssh root@8.138.114.34 'tail -f /opt/lightscript/backend/backend.log'

# 应用业务日志
ssh root@8.138.114.34 'tail -f /opt/lightscript/logs/lightscript-server.log'
```

---

## 📂 服务器目录结构

```
/opt/lightscript/
├── backend/
│   ├── server.jar              # 后端JAR包 (48MB)
│   ├── application-prod.yml    # 生产环境配置
│   ├── backend.log            # 后端日志
│   └── backend.pid            # 后端进程ID
├── frontend/
│   ├── index.html             # 前端入口
│   └── assets/                # 前端资源 (CSS, JS)
├── data/
│   └── lightscript.mv.db      # H2数据库文件
├── logs/
│   ├── lightscript-server.log # 应用日志
│   ├── nginx-access.log       # Nginx访问日志
│   └── nginx-error.log        # Nginx错误日志
└── scripts/
    ├── start-backend.sh       # 启动后端
    ├── start-frontend.sh      # 启动前端（Nginx）
    ├── stop-all.sh            # 停止所有服务
    └── restart-all.sh         # 重启所有服务
```

---

## 🔧 技术栈

### 前端
- React 18
- Ant Design 5
- Tailwind CSS
- Vite
- Nginx (Web服务器)

### 后端
- Spring Boot 2.7.18
- Spring Security
- Spring Data JPA
- H2 Database
- JWT认证

---

## 🚀 更新部署

当代码有更新时，运行：

```bash
./scripts/mac/deploy-to-aliyun.sh
```

脚本会自动：
1. ✅ 本地构建前后端
2. ✅ 备份现有部署
3. ✅ 停止服务
4. ✅ 上传新版本
5. ✅ 配置Nginx
6. ✅ 启动服务

---

## ⚠️ 重要提示

### 安全建议
1. 修改默认密码
2. 配置HTTPS（使用Let's Encrypt）
3. 定期备份数据库文件
4. 限制SSH访问IP
5. 配置防火墙规则

### 阿里云安全组
确保以下端口已开放：
- ✅ 80 (HTTP)
- ✅ 3000 (前端)
- ✅ 8080 (后端API)
- ✅ 22 (SSH)

⚠️ 配置后需要在阿里云控制台"应用到实例"才能生效

### 备份策略
```bash
# 备份数据库
ssh root@8.138.114.34 'cp /opt/lightscript/data/lightscript.mv.db /opt/lightscript/data/lightscript.mv.db.backup.$(date +%Y%m%d)'

# 下载备份到本地
scp root@8.138.114.34:/opt/lightscript/data/lightscript.mv.db.backup.* ./backups/
```

---

## 📚 相关文档

- [详细部署指南](./DEPLOYMENT_ALIYUN.md)
- [Nginx部署说明](./NGINX_DEPLOYMENT.md)
- [安全组配置](./ALIYUN_SECURITY_GROUP.md)
- [快速部署指南](./QUICK_DEPLOY.md)
- [项目结构说明](./PROJECT_STRUCTURE.md)

---

## 🎉 部署验证清单

- [x] 后端服务启动成功
- [x] 前端服务启动成功（Nginx）
- [x] 数据库初始化完成
- [x] 默认用户创建成功
- [x] 80端口可访问
- [x] 3000端口可访问
- [x] 8080端口可访问
- [x] SSH免密登录配置完成
- [x] Nginx配置正确
- [x] 日志文件正常生成

---

**部署状态**: ✅ 成功  
**最后更新**: 2026-02-25 16:54
