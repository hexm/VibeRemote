# 阿里云部署脚本说明

## 📁 脚本列表

### 1. setup-ssh-key.sh
**用途**: 配置SSH免密登录

**使用方法**:
```bash
./scripts/mac/setup-ssh-key.sh
```

**说明**:
- 仅需运行一次
- 会生成SSH密钥对
- 需要输入一次服务器密码
- 配置完成后可以免密登录服务器

### 2. deploy-to-aliyun.sh
**用途**: 一键部署到阿里云

**使用方法**:
```bash
./scripts/mac/deploy-to-aliyun.sh
```

**说明**:
- 完全自动化，无需输入密码
- 自动构建前后端
- 自动上传到服务器
- 自动启动服务

## 🔧 配置说明

### 修改服务器地址

编辑脚本中的以下变量：

```bash
SERVER_IP="8.138.114.34"      # 服务器IP
SERVER_USER="root"            # SSH用户名
REMOTE_DIR="/opt/lightscript" # 远程部署目录
BACKEND_PORT="8080"           # 后端端口
FRONTEND_PORT="3000"          # 前端端口
```

## 🚀 快速开始

```bash
# 1. 配置SSH免密登录（仅需一次）
./scripts/mac/setup-ssh-key.sh

# 2. 部署到阿里云
./scripts/mac/deploy-to-aliyun.sh
```

## 📝 注意事项

1. 确保本地已安装 Java、Maven、Node.js
2. 确保服务器已开放 8080 和 3000 端口
3. 首次部署需要配置SSH免密登录
4. 部署过程约需 3-5 分钟

## 🔍 故障排查

### SSH连接失败
```bash
# 测试SSH连接
ssh root@8.138.114.34

# 如果失败，重新配置SSH密钥
./scripts/mac/setup-ssh-key.sh
```

### 构建失败
```bash
# 检查Java版本
java -version

# 检查Maven版本
mvn -version

# 检查Node.js版本
node -v
npm -v
```

### 服务无法访问
1. 检查阿里云安全组规则
2. 检查服务器防火墙
3. 查看服务日志
