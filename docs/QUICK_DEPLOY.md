# 🚀 LightScript 快速部署到阿里云

## 两步完成部署

### 步骤1: 配置SSH免密登录（仅需一次）

```bash
./scripts/mac/setup-ssh-key.sh
```

**需要输入一次服务器密码**

### 步骤2: 一键部署

```bash
./scripts/mac/deploy-to-aliyun.sh
```

**完全自动化，无需输入密码**

---

## 部署完成后

### 访问地址
- 前端: http://8.138.114.34:3000
- 后端: http://8.138.114.34:8080

### 默认账号
- 管理员: `admin` / `admin123`
- 普通用户: `user` / `user123`

---

## 常用命令

```bash
# 查看后端日志
ssh root@8.138.114.34 'tail -f /opt/lightscript/backend/backend.log'

# 重启服务
ssh root@8.138.114.34 '/opt/lightscript/scripts/restart-all.sh'

# 停止服务
ssh root@8.138.114.34 '/opt/lightscript/scripts/stop-all.sh'
```

---

详细文档请查看: [DEPLOYMENT_ALIYUN.md](./DEPLOYMENT_ALIYUN.md)
