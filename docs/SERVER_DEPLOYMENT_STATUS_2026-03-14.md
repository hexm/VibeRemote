# LightScript 服务器部署状况报告

**服务器**: 阿里云 8.138.114.34  
**更新时间**: 2026年3月14日 16:28  
**部署状态**: ✅ 正常运行

## 📁 目录结构

### 主部署目录: `/opt/lightscript/`
```
/opt/lightscript/
├── backend/          # 后端应用
├── frontend/         # 前端静态文件  
├── portal/           # 门户网站
├── logs/             # Nginx日志
└── scripts/          # 管理脚本
```

## 🚀 服务运行状况

### 后端服务 (Spring Boot)
- **运行路径**: `/opt/lightscript/backend/`
- **JAR文件**: `server.jar` (48MB)
- **进程ID**: `147901`
- **启动命令**: `java -jar server.jar --spring.profiles.active=prod`
- **监听端口**: `8080`
- **状态**: ✅ 正常运行 (CPU: 3.4%, 内存: 24.2%)

### 前端服务 (Nginx)
- **静态文件路径**: `/opt/lightscript/frontend/`
- **管理后台端口**: `3000`
- **Nginx配置**: 代理到后端8080端口
- **状态**: ✅ 正常运行

### 门户网站 (Nginx)
- **静态文件路径**: `/opt/lightscript/portal/`
- **访问端口**: `80` (主站)
- **状态**: ✅ 正常运行

## 📋 配置文件

### 后端配置
- **主配置**: `/opt/lightscript/backend/application.yml`
- **生产配置**: `/opt/lightscript/backend/application-prod.yml`
- **开发配置**: `/opt/lightscript/backend/application-dev.yml`

### 数据库配置
- **类型**: MySQL 8.0
- **地址**: `8.138.114.34:3306`
- **数据库**: `lightscript`
- **连接池**: HikariCP (最大10个连接)

## 📊 日志系统

### 应用日志
- **主日志**: `/opt/lightscript/backend/backend.log` (552KB)
- **服务器日志**: `/opt/lightscript/backend/logs/lightscript-server.log` (560KB)
- **错误日志**: `/opt/lightscript/backend/logs/lightscript-server-error.log` (556KB)
- **业务日志**: `/opt/lightscript/backend/logs/lightscript-business.log` (20KB)

### Web服务器日志
- **Nginx访问日志**: `/opt/lightscript/logs/nginx-access.log`
- **Nginx错误日志**: `/opt/lightscript/logs/nginx-error.log`
- **管理后台访问日志**: `/opt/lightscript/logs/nginx-admin-access.log`
- **管理后台错误日志**: `/opt/lightscript/logs/nginx-admin-error.log`

### 任务日志
- **存储路径**: `logs/tasks/` (相对于应用运行目录)
- **格式**: `logs/tasks/YYYY/MM/taskId_agentId_executionNumber_startTime.log`
- **保留期**: 90天

## 📁 文件存储

## 📁 文件存储

### 上传文件存储
- **存储路径**: `/opt/lightscript/backend/files/`
- **文件命名**: `{timestamp}_{originalFilename}`
- **示例文件**: `1773477302261_execution_20_1.log`
- **文件大小限制**: 100MB
- **支持校验**: MD5 和 SHA256

### 文件管理
- **数据库记录**: 文件元信息存储在MySQL数据库
- **物理文件**: 存储在应用运行目录的 `files/` 子目录
- **文件ID**: 格式为 `F{timestamp}`，如 `F1773477302261`

### 最近上传文件
- **文件名**: `execution_20_1.log`
- **存储位置**: `/opt/lightscript/backend/files/1773477302261_execution_20_1.log`
- **文件大小**: 287 bytes
- **上传时间**: 2026-03-14 16:35
- **文件内容**: Shell脚本执行日志

### 静态资源
- **前端资源**: `/opt/lightscript/frontend/assets/`
- **门户资源**: `/opt/lightscript/portal/assets/`
- **Logo文件**: 
  - `/opt/lightscript/frontend/logo.svg`
  - `/opt/lightscript/portal/assets/logo.svg`

## 🔧 管理脚本

### 启动脚本
- **后端启动**: `/opt/lightscript/scripts/start-backend.sh`
- **前端启动**: `/opt/lightscript/scripts/start-frontend.sh`
- **全部重启**: `/opt/lightscript/scripts/restart-all.sh`
- **停止服务**: `/opt/lightscript/scripts/stop-all.sh`

### 进程管理
- **PID文件**: `/opt/lightscript/backend/backend.pid`
- **当前PID**: `147901`

## 🌐 网络配置

### 端口映射
- **80**: 门户网站 (主站)
- **3000**: 管理后台
- **8080**: 后端API服务

### 访问地址
- **门户网站**: http://8.138.114.34
- **管理后台**: http://8.138.114.34/admin 或 http://8.138.114.34:3000
- **后端API**: http://8.138.114.34:8080

## 🔐 安全配置

### JWT认证
- **密钥**: 生产环境专用密钥
- **过期时间**: 24小时

### CORS配置
- **允许来源**: 
  - http://8.138.114.34:3000
  - http://localhost:3000
- **允许方法**: GET, POST, PUT, DELETE, OPTIONS

## 📈 性能监控

### 系统资源
- **JVM内存**: 148MB/416MB (36%)
- **系统内存**: 监控中
- **CPU使用率**: 监控中
- **磁盘使用**: 监控中

### 数据库连接
- **活跃连接**: 监控中
- **连接池状态**: HikariCP管理

## 🚨 注意事项

1. **Agent进程**: 发现有旧的Agent进程仍在运行 (PID: 134943)
2. **文件上传**: 需要确认文件上传的具体存储路径
3. **日志轮转**: 建议配置日志轮转避免日志文件过大
4. **备份策略**: 建议定期备份数据库和重要配置文件

---

**报告生成**: Kiro AI Assistant  
**下次检查**: 建议每周检查一次服务器状态