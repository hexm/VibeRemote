# 🎉 阿里云一键部署已配置完成

## ✅ 已创建的文件

### 部署脚本
- `scripts/mac/setup-ssh-key.sh` - SSH免密登录配置脚本
- `scripts/mac/deploy-to-aliyun.sh` - 一键部署脚本

### 配置文件
- `server/src/main/resources/application-prod.yml` - 生产环境配置

### 文档
- `DEPLOYMENT_ALIYUN.md` - 详细部署文档
- `QUICK_DEPLOY.md` - 快速部署指南
- `scripts/mac/README_DEPLOY.md` - 脚本使用说明

## 🚀 开始部署

### 第一次部署（需要输入一次密码）

```bash
# 步骤1: 配置SSH免密登录
./scripts/mac/setup-ssh-key.sh

# 步骤2: 一键部署
./scripts/mac/deploy-to-aliyun.sh
```

### 后续更新（完全自动化）

```bash
# 直接运行部署脚本即可
./scripts/mac/deploy-to-aliyun.sh
```

## 📍 部署信息

- **服务器IP**: 8.138.114.34
- **后端端口**: 8080
- **前端端口**: 3000
- **部署目录**: /opt/lightscript

## 🌐 访问地址

部署完成后访问：
- **前端**: http://8.138.114.34:3000
- **后端API**: http://8.138.114.34:8080

## 🔐 默认账号

- 管理员: admin / admin123
- 普通用户: user / user123

## 📊 部署流程

```
本地构建 → 创建部署包 → 上传到服务器 → 配置环境 → 启动服务
   ↓           ↓            ↓              ↓           ↓
 Maven      打包文件      SSH传输      安装依赖    启动进程
  npm       压缩优化      免密上传      配置防火墙   后台运行
```

## 🛠️ 服务管理

```bash
# 查看日志
ssh root@8.138.114.34 'tail -f /opt/lightscript/backend/backend.log'

# 重启服务
ssh root@8.138.114.34 '/opt/lightscript/scripts/restart-all.sh'

# 停止服务
ssh root@8.138.114.34 '/opt/lightscript/scripts/stop-all.sh'
```

## ⚠️ 重要提示

1. **首次部署**: 需要运行 `setup-ssh-key.sh` 配置SSH免密登录
2. **安全组**: 确保阿里云安全组已开放 8080 和 3000 端口
3. **防火墙**: 脚本会自动配置服务器防火墙
4. **备份**: 每次部署会自动备份旧版本

## 📞 需要帮助？

查看详细文档：
- [完整部署指南](./DEPLOYMENT_ALIYUN.md)
- [快速开始](./QUICK_DEPLOY.md)
- [脚本说明](./scripts/mac/README_DEPLOY.md)
