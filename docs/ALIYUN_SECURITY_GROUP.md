# 阿里云安全组配置指南

## 问题说明

部署完成后，如果无法访问前端（3000端口）或后端（8080端口），需要在阿里云控制台配置安全组规则。

## 配置步骤

### 方法一：通过阿里云控制台（推荐）

1. **登录阿里云控制台**
   - 访问: https://ecs.console.aliyun.com/
   - 登录你的阿里云账号

2. **进入ECS实例管理**
   - 点击左侧菜单 "实例与镜像" → "实例"
   - 找到你的服务器（IP: 8.138.114.34）

3. **配置安全组**
   - 点击实例右侧的 "更多" → "网络和安全组" → "安全组配置"
   - 或者点击 "安全组" 标签页

4. **添加入方向规则**
   - 点击 "配置规则" 或 "添加规则"
   - 点击 "入方向" → "手动添加"

5. **添加3000端口规则**
   ```
   授权策略: 允许
   优先级: 1
   协议类型: TCP
   端口范围: 3000/3000
   授权对象: 0.0.0.0/0
   描述: LightScript前端服务
   ```

6. **添加8080端口规则**
   ```
   授权策略: 允许
   优先级: 1
   协议类型: TCP
   端口范围: 8080/8080
   授权对象: 0.0.0.0/0
   描述: LightScript后端API
   ```

7. **保存配置**
   - 点击 "保存" 按钮
   - 规则立即生效，无需重启服务器

### 方法二：使用阿里云CLI

如果你安装了阿里云CLI工具，可以使用命令行配置：

```bash
# 获取安全组ID
aliyun ecs DescribeInstances --InstanceIds '["你的实例ID"]'

# 添加3000端口规则
aliyun ecs AuthorizeSecurityGroup \
  --SecurityGroupId sg-xxxxxx \
  --IpProtocol tcp \
  --PortRange 3000/3000 \
  --SourceCidrIp 0.0.0.0/0 \
  --Description "LightScript前端服务"

# 添加8080端口规则
aliyun ecs AuthorizeSecurityGroup \
  --SecurityGroupId sg-xxxxxx \
  --IpProtocol tcp \
  --PortRange 8080/8080 \
  --SourceCidrIp 0.0.0.0/0 \
  --Description "LightScript后端API"
```

## 验证配置

配置完成后，使用以下命令验证端口是否可访问：

```bash
# 测试后端端口
curl -I http://8.138.114.34:8080

# 测试前端端口
curl -I http://8.138.114.34:3000
```

或者直接在浏览器中访问：
- 前端: http://8.138.114.34:3000
- 后端: http://8.138.114.34:8080

## 安全建议

### 1. 限制访问IP（推荐）

如果只需要特定IP访问，可以修改授权对象：

```
授权对象: 你的公网IP/32
```

查看你的公网IP：
```bash
curl ifconfig.me
```

### 2. 使用Nginx反向代理

更安全的做法是只开放80/443端口，使用Nginx反向代理：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端
    location / {
        proxy_pass http://localhost:3000;
    }

    # 后端API
    location /api {
        proxy_pass http://localhost:8080;
    }
}
```

这样只需要开放80端口，3000和8080端口可以保持内网访问。

### 3. 配置HTTPS

使用Let's Encrypt免费SSL证书：

```bash
# 安装certbot
yum install -y certbot python3-certbot-nginx

# 获取证书
certbot --nginx -d your-domain.com
```

## 常见问题

### Q: 配置后仍然无法访问？

A: 检查以下几点：
1. 安全组规则是否保存成功
2. 服务是否正常运行：`ssh root@8.138.114.34 'ps aux | grep -E "(java|python3)"'`
3. 服务器防火墙是否开放：`ssh root@8.138.114.34 'iptables -L -n'`
4. 是否有其他安全策略限制

### Q: 如何查看当前安全组规则？

A: 在阿里云控制台：
1. 进入ECS实例
2. 点击 "安全组" 标签
3. 点击安全组ID
4. 查看 "入方向" 规则

### Q: 误删了规则怎么办？

A: 重新添加规则即可，参考上面的配置步骤。

## 快速配置清单

- [ ] 登录阿里云控制台
- [ ] 找到ECS实例（8.138.114.34）
- [ ] 进入安全组配置
- [ ] 添加3000端口规则（TCP）
- [ ] 添加8080端口规则（TCP）
- [ ] 保存配置
- [ ] 测试访问

## 相关文档

- [阿里云安全组官方文档](https://help.aliyun.com/document_detail/25471.html)
- [部署指南](./DEPLOYMENT_ALIYUN.md)
- [快速部署](./QUICK_DEPLOY.md)
