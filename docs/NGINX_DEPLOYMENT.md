# Nginx部署说明

## 部署变更

由于阿里云安全组配置的限制，我们已将前端部署方式从Python HTTP Server改为Nginx，并配置了80端口访问。

## 当前配置

### 端口说明
- **80端口**: 前端访问（通过Nginx）✅ 可访问
- **3000端口**: 前端直接访问（通过Nginx）❌ 需要在阿里云安全组开放
- **8080端口**: 后端API ✅ 可访问

### Nginx配置文件
位置: `/etc/nginx/conf.d/lightscript.conf`

```nginx
# 3000端口 - 前端直接访问
server {
    listen 3000;
    server_name _;
    
    root /opt/lightscript/frontend;
    index index.html;
    
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}

# 80端口 - 前端+后端API代理
server {
    listen 80;
    server_name _;
    
    # 前端
    location / {
        root /opt/lightscript/frontend;
        try_files $uri $uri/ /index.html;
    }
    
    # 后端API代理
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

## 访问方式

### 方式一：通过80端口（推荐）
- **前端**: http://8.138.114.34
- **后端API**: http://8.138.114.34/api/

优点：
- 无需额外配置安全组
- 前后端统一域名
- 支持API代理

### 方式二：通过独立端口
- **前端**: http://8.138.114.34:3000
- **后端**: http://8.138.114.34:8080

需要：
- 在阿里云安全组开放3000端口

## 服务管理

### 启动服务
```bash
# 启动Nginx
ssh root@8.138.114.34 'systemctl start nginx'

# 启动后端
ssh root@8.138.114.34 '/opt/lightscript/scripts/start-backend.sh'
```

### 停止服务
```bash
# 停止Nginx
ssh root@8.138.114.34 'systemctl stop nginx'

# 停止后端
ssh root@8.138.114.34 '/opt/lightscript/scripts/stop-all.sh'
```

### 重启服务
```bash
# 重启Nginx
ssh root@8.138.114.34 'systemctl restart nginx'

# 重启后端
ssh root@8.138.114.34 '/opt/lightscript/scripts/restart-all.sh'
```

### 查看状态
```bash
# Nginx状态
ssh root@8.138.114.34 'systemctl status nginx'

# 查看端口监听
ssh root@8.138.114.34 'netstat -tlnp | grep -E "(80|3000|8080)"'
```

### 查看日志
```bash
# Nginx访问日志
ssh root@8.138.114.34 'tail -f /opt/lightscript/logs/nginx-access.log'

# Nginx错误日志
ssh root@8.138.114.34 'tail -f /opt/lightscript/logs/nginx-error.log'

# 后端日志
ssh root@8.138.114.34 'tail -f /opt/lightscript/backend/backend.log'
```

## 配置修改

### 修改Nginx配置
```bash
# 编辑配置文件
ssh root@8.138.114.34 'vi /etc/nginx/conf.d/lightscript.conf'

# 测试配置
ssh root@8.138.114.34 'nginx -t'

# 重新加载配置
ssh root@8.138.114.34 'nginx -s reload'
```

### 添加HTTPS支持
```bash
# 安装certbot
ssh root@8.138.114.34 'yum install -y certbot python3-certbot-nginx'

# 获取证书（需要域名）
ssh root@8.138.114.34 'certbot --nginx -d your-domain.com'
```

## 前端配置

如果使用80端口访问，前端需要配置API代理路径。

编辑 `web-modern/.env.production`:
```env
VITE_API_BASE_URL=/api
```

这样前端会通过 `/api` 路径访问后端，Nginx会自动代理到 `http://localhost:8080/api/`。

## 故障排查

### 无法访问80端口
1. 检查Nginx是否运行: `systemctl status nginx`
2. 检查端口监听: `netstat -tlnp | grep 80`
3. 检查阿里云安全组是否开放80端口

### 无法访问3000端口
1. 检查Nginx是否监听3000: `netstat -tlnp | grep 3000`
2. 在阿里云控制台开放3000端口
3. 检查服务器防火墙: `iptables -L -n`

### API请求失败
1. 检查后端是否运行: `ps aux | grep java`
2. 检查后端日志: `tail -f /opt/lightscript/backend/backend.log`
3. 检查Nginx代理配置: `nginx -t`

### 静态资源404
1. 检查文件是否存在: `ls -la /opt/lightscript/frontend/`
2. 检查Nginx配置中的root路径
3. 检查文件权限: `chmod -R 755 /opt/lightscript/frontend/`

## 性能优化

### 启用Gzip压缩
编辑 `/etc/nginx/nginx.conf`:
```nginx
gzip on;
gzip_types text/plain text/css application/json application/javascript text/xml application/xml;
gzip_min_length 1000;
```

### 配置缓存
```nginx
location /assets/ {
    expires 1y;
    add_header Cache-Control "public, immutable";
}
```

### 限制请求大小
```nginx
client_max_body_size 10M;
```

## 相关文档
- [部署指南](./DEPLOYMENT_ALIYUN.md)
- [安全组配置](./ALIYUN_SECURITY_GROUP.md)
- [快速部署](./QUICK_DEPLOY.md)
