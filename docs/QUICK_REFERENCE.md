# LightScript 快速参考指南

## 🌐 访问地址

### 生产环境
- **前端**: http://8.138.114.34:3000 或 http://8.138.114.34
- **后端API**: http://8.138.114.34:8080

### 默认账号
- **管理员**: admin / admin123
- **普通用户**: user / user123

---

## 🚀 常用命令

### 本地Agent管理
```bash
# 启动Agent（连接阿里云）
./scripts/mac/start-agent-aliyun.sh

# 启动Agent（连接本地）
./scripts/mac/start-agent.sh

# 查看Agent日志（在运行的终端中）
# Agent会自动打印资源使用情况
```

### 服务器管理
```bash
# 查看服务状态
ssh root@8.138.114.34 'systemctl status nginx'
ssh root@8.138.114.34 'ps aux | grep java'

# 查看日志
ssh root@8.138.114.34 'tail -f /opt/lightscript/backend/backend.log'
ssh root@8.138.114.34 'tail -f /opt/lightscript/logs/nginx-access.log'

# 重启服务
ssh root@8.138.114.34 '/opt/lightscript/scripts/restart-all.sh'

# 查看端口监听
ssh root@8.138.114.34 'netstat -tlnp | grep -E "(80|3000|8080)"'
```

### 部署更新
```bash
# 完整部署（前端+后端）
./scripts/mac/deploy-to-aliyun.sh

# 只更新前端
cd web-modern && npm run build
scp -r dist/* root@8.138.114.34:/opt/lightscript/frontend/

# 只更新后端
mvn clean package -DskipTests
scp server/target/server-*.jar root@8.138.114.34:/opt/lightscript/backend/server.jar
ssh root@8.138.114.34 '/opt/lightscript/scripts/restart-all.sh'

# 只更新Agent
mvn -q -f agent/pom.xml clean package -DskipTests
# 然后重启本地Agent
```

### Git操作
```bash
# 查看状态
git status

# 提交更改
git add -A
git commit -m "描述信息"

# 推送到远程
git push

# 查看提交历史
git log --oneline -10
```

---

## 📊 功能清单

### ✅ 已完成功能
- [x] 用户登录认证（JWT）
- [x] 节点管理（Agent列表、状态监控）
- [x] 任务管理（创建、执行、查看日志）
- [x] 批量任务（多节点批量执行）
- [x] 脚本管理
- [x] 仪表盘
- [x] 资源监控（CPU、内存）
- [x] 自动心跳和重连
- [x] 一键部署脚本

### 🔄 待优化功能
- [ ] 任务管理功能优化（参考旧版web）
- [ ] 任务历史记录
- [ ] 日志自动刷新
- [ ] 任务重启功能
- [ ] 定时任务
- [ ] 任务模板

---

## 🧪 测试流程

### 1. 测试Agent连接
1. 启动本地Agent: `./scripts/mac/start-agent-aliyun.sh`
2. 访问Web界面: http://8.138.114.34:3000
3. 登录: admin / admin123
4. 进入"节点管理"，确认看到本地Agent
5. 检查状态为"在线"（绿色）

### 2. 测试任务执行
1. 进入"任务管理"
2. 点击"创建任务"
3. 填写信息：
   - 任务名称: 测试任务
   - 脚本语言: bash
   - 脚本内容:
     ```bash
     echo "Hello from LightScript!"
     hostname
     date
     uname -a
     ```
   - 执行节点: 选择你的Agent
4. 点击"创建"
5. 等待任务执行完成
6. 点击"查看日志"查看输出

### 3. 测试批量任务
1. 切换到"批量任务"标签
2. 点击"创建批量任务"
3. 选择多个节点（如果有）
4. 输入脚本并执行
5. 查看批量任务进度和统计

---

## 📁 重要文件路径

### 服务器端
```
/opt/lightscript/
├── backend/
│   ├── server.jar              # 后端JAR包
│   ├── application-prod.yml    # 生产环境配置
│   └── backend.log            # 后端日志
├── frontend/                   # 前端文件
├── data/
│   └── lightscript.mv.db      # H2数据库
├── logs/                       # 日志目录
└── scripts/                    # 管理脚本
```

### 本地
```
~/git/LightScript/
├── agent/                      # Agent模块
├── server/                     # 后端模块
├── web-modern/                 # 现代化前端
├── scripts/mac/                # Mac脚本
└── docs/                       # 文档目录
```

---

## 🔧 故障排查

### Agent无法连接
1. 检查服务器是否运行: `curl http://8.138.114.34:8080`
2. 检查8080端口是否开放
3. 查看Agent日志中的错误信息
4. 确认注册令牌正确: `dev-register-token`

### 前端无法访问
1. 检查Nginx状态: `ssh root@8.138.114.34 'systemctl status nginx'`
2. 检查80和3000端口是否开放
3. 清除浏览器缓存（Cmd+Shift+R）
4. 查看Nginx错误日志

### 任务执行失败
1. 检查Agent是否在线
2. 查看任务日志中的错误信息
3. 检查脚本语法是否正确
4. 确认Agent有执行权限

### 资源使用显示异常
1. 强制刷新浏览器（Cmd+Shift+R）
2. 检查Agent是否上报了totalMemMb
3. 查看Agent日志中的资源使用打印
4. 等待下一次心跳更新（30秒）

---

## 📚 文档索引

- [部署指南](./DEPLOYMENT_ALIYUN.md) - 详细部署步骤
- [快速部署](./QUICK_DEPLOY.md) - 两步完成部署
- [Nginx部署](./NGINX_DEPLOYMENT.md) - Nginx配置说明
- [安全组配置](./ALIYUN_SECURITY_GROUP.md) - 阿里云安全组
- [Agent配置](./AGENT_SETUP.md) - Agent安装配置
- [项目结构](./PROJECT_STRUCTURE.md) - 项目目录说明
- [部署总结](./DEPLOY_SUMMARY.md) - 部署状态总结
- [部署成功](./DEPLOYMENT_SUCCESS.md) - 完整部署记录
- [任务管理优化](./TASK_MANAGEMENT_IMPROVEMENTS.md) - 待优化功能

---

## 💡 最佳实践

### 安全建议
1. 修改默认密码
2. 修改JWT密钥
3. 修改注册令牌
4. 配置HTTPS
5. 限制SSH访问IP
6. 定期备份数据库

### 运维建议
1. 定期查看日志
2. 监控服务器资源
3. 定期备份数据库文件
4. 配置日志轮转
5. 设置告警通知

### 开发建议
1. 本地测试后再部署
2. 使用Git管理代码
3. 编写清晰的提交信息
4. 保持文档更新
5. 遵循代码规范

---

## 🎯 下一步计划

1. **任务管理优化** - 参考旧版web，添加缺失功能
2. **定时任务** - 支持cron表达式
3. **任务模板** - 保存常用脚本为模板
4. **告警通知** - 任务失败时发送通知
5. **操作审计** - 记录用户操作日志
6. **性能优化** - 提升系统性能
7. **HTTPS配置** - 使用Let's Encrypt

---

**最后更新**: 2026-02-25  
**系统状态**: ✅ 正常运行
