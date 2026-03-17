# Nginx 配置文件

本目录包含LightScript系统的Nginx配置文件。

## 📁 文件说明

- `lightscript.conf` - 主要的Nginx配置文件

## 🚀 配置说明

### lightscript.conf

这个配置文件设置了两个服务器块：

#### 1. 主站服务器 (端口80)
- **用途**: 门户网站和API代理
- **根目录**: `/var/www/html`
- **功能**:
  - 静态文件服务（门户网站）
  - API请求代理到后端服务器 (localhost:8080)
  - 管理后台代理
  - 安装脚本的MIME类型设置
  - 静态资源缓存优化

#### 2. 管理后台服务器 (端口3000)
- **用途**: 前端管理界面
- **根目录**: `/opt/lightscript/frontend`
- **功能**:
  - React应用的静态文件服务
  - API请求代理到后端服务器
  - 资源缓存优化

## 📦 部署步骤

### 1. 复制配置文件
```bash
# 复制到nginx配置目录
sudo cp lightscript.conf /etc/nginx/sites-available/
sudo ln -s /etc/nginx/sites-available/lightscript.conf /etc/nginx/sites-enabled/
```

### 2. 创建必要目录
```bash
# 门户网站目录
sudo mkdir -p /var/www/html

# 前端应用目录
sudo mkdir -p /opt/lightscript/frontend
```

### 3. 部署文件
```bash
# 部署门户网站文件
sudo cp -r portal/* /var/www/html/

# 部署前端应用文件
sudo cp -r web/dist/* /opt/lightscript/frontend/
```

### 4. 测试和重启
```bash
# 测试配置
sudo nginx -t

# 重启nginx
sudo systemctl restart nginx
```

## 🔧 配置调整

### 修改后端服务器地址
如果后端服务器不在localhost:8080，需要修改配置文件中的proxy_pass地址：

```nginx
location /api/ {
    proxy_pass http://your-backend-server:8080/api/;
    # ... 其他配置
}
```

### 修改静态文件路径
根据实际部署情况调整root路径：

```nginx
server {
    # ...
    root /your/custom/path;
    # ...
}
```

## 📋 日志文件

- 主站访问日志: `/var/log/nginx/access.log`
- 主站错误日志: `/var/log/nginx/error.log`
- 管理后台访问日志: `/var/log/nginx/admin-access.log`
- 管理后台错误日志: `/var/log/nginx/admin-error.log`

## ⚠️ 注意事项

1. 确保后端服务器在localhost:8080运行
2. 确保nginx有权限访问静态文件目录
3. 如果使用HTTPS，需要添加SSL配置
4. 生产环境建议设置适当的安全头部