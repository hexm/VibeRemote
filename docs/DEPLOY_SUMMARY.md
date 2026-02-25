# LightScript 生产环境部署总结

## 部署信息

- **部署时间**: 2026-02-25 16:42
- **服务器**: 阿里云ECS
- **IP地址**: 8.138.114.34
- **部署路径**: /opt/lightscript/

## 服务状态

### 后端服务
- **端口**: 8080
- **状态**: ✅ 运行中
- **进程ID**: 35953
- **访问地址**: http://8.138.114.34:8080
- **启动时间**: 7.761秒
- **Spring Boot版本**: 2.7.18
- **Java版本**: 1.8.0_482

### 前端服务
- **端口**: 3000
- **状态**: ✅ 运行中
- **进程ID**: 35972
- **访问地址**: http://8.138.114.34:3000

## 默认账号

- **管理员**: admin / admin123
- **普通用户**: user / user123

## 快速命令

```bash
# 查看后端日志
ssh root@8.138.114.34 'tail -f /opt/lightscript/backend/backend.log'

# 查看前端日志
ssh root@8.138.114.34 'tail -f /opt/lightscript/frontend/frontend.log'

# 重启服务
ssh root@8.138.114.34 '/opt/lightscript/scripts/restart-all.sh'

# 停止服务
ssh root@8.138.114.34 '/opt/lightscript/scripts/stop-all.sh'

# 查看服务进程
ssh root@8.138.114.34 'ps aux | grep -E "(java|python3)" | grep -v grep'
```

## 部署流程

1. ✅ 配置SSH免密登录
2. ✅ 本地构建后端和前端
3. ✅ 创建部署包
4. ✅ 上传到服务器
5. ✅ 配置服务器环境
6. ✅ 启动服务

## 部署验证

- ✅ 后端服务正常启动（Spring Boot 2.7.18）
- ✅ 前端服务正常运行（Python HTTP Server）
- ✅ 数据库初始化完成（H2 Database）
- ✅ 默认用户创建成功
- ✅ JPA实体管理器初始化完成
- ✅ Tomcat启动在8080端口

## 服务器目录结构

```
/opt/lightscript/
├── backend/
│   ├── server.jar              # 后端JAR包 (48MB)
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

## 注意事项

1. 首次登录后请修改默认密码
2. 建议配置HTTPS和域名
3. 定期备份数据库文件：/opt/lightscript/data/lightscript.mv.db
4. 监控服务器资源使用情况
5. 防火墙已配置开放8080和3000端口

## 更新部署

当代码有更新时，只需重新运行部署脚本：

```bash
./scripts/mac/deploy-to-aliyun.sh
```

脚本会自动：
1. 备份现有部署
2. 停止服务
3. 部署新版本
4. 启动服务

## 相关文档

- [详细部署指南](./DEPLOYMENT_ALIYUN.md)
- [快速部署指南](./QUICK_DEPLOY.md)
- [项目结构说明](./PROJECT_STRUCTURE.md)
