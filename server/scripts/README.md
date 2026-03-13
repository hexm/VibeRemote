# Server Scripts 目录

这个目录包含所有与LightScript Server相关的脚本。

## 脚本分类

### 启动脚本
- `start-server.sh` - 启动LightScript Server (Linux/macOS)
- `start-server.bat` - 启动LightScript Server (Windows)
- `start-with-mysql.sh` - 使用MySQL数据库启动Server

### 数据库脚本
- `migrate-to-mysql.sh` - 从H2数据库迁移到MySQL
- `configure-mysql-aliyun.sh` - 配置阿里云MySQL连接
- `install-mysql-aliyun.sh` - 在阿里云服务器上安装MySQL
- `setup-mysql-aliyun.sh` - 设置阿里云MySQL环境

### 部署脚本
- `deploy-to-aliyun.sh` - 部署到阿里云服务器
- `setup-ssh-key.sh` - 设置SSH密钥
- `test-aliyun.sh` - 测试阿里云部署

### 文档
- `README_DEPLOY.md` - 详细的部署指南

## 使用方法

### 本地开发
```bash
# 启动Server (H2数据库)
./server/scripts/start-server.sh

# 启动Server (MySQL数据库)
./server/scripts/start-with-mysql.sh
```

### 数据库迁移
```bash
# 从H2迁移到MySQL
./server/scripts/migrate-to-mysql.sh
```

### 生产部署
```bash
# 部署到阿里云
./server/scripts/deploy-to-aliyun.sh

# 测试部署
./server/scripts/test-aliyun.sh
```

## 环境要求

- Java 8或更高版本
- Maven 3.6或更高版本
- MySQL 8.0 (如果使用MySQL)

## 配置说明

所有脚本都会自动检测项目根目录，并使用相对路径执行操作。脚本执行前会自动检查必要的依赖和环境。

## 日志文件

- Server日志: `server/logs/server.log`
- 启动日志: 脚本执行目录下的临时日志文件

## 故障排查

如果脚本执行失败，请检查：
1. Java和Maven是否正确安装
2. 网络连接是否正常
3. 数据库连接配置是否正确
4. 端口8080是否被占用

更多详细信息请参考 `README_DEPLOY.md`。