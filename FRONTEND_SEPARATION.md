# 前后端分离部署指南

## 概述

LightScript 项目已经实现了完整的前后端分离架构：
- **后端**: Spring Boot REST API 服务
- **前端**: Vue.js 3 单页应用
- **通信**: RESTful API + JWT 认证

## 项目结构

```
lightScript/
├── server/                 # 后端 Spring Boot 项目
├── agent/                  # 客户端 Agent 项目  
├── web/                    # 前端 Vue.js 项目 (新增)
└── demo/                   # 参考代码
```

## 前端项目 (web/)

### 技术栈
- Vue.js 3 (CDN 方式引入)
- Element Plus UI 组件库
- Axios HTTP 客户端
- 原生 JavaScript (无需构建工具)

### 文件结构
```
web/
├── index.html              # 主页面
├── css/style.css          # 样式文件
├── js/
│   ├── config.js          # 配置文件
│   └── app.js             # 应用逻辑
├── package.json           # 项目配置
├── start-web.bat/sh       # 启动脚本
└── README.md             # 前端说明
```

## 部署方式

### 方式一：集成部署 (开发环境)

前端文件放在后端 `static` 目录中，由 Spring Boot 提供静态文件服务：

```bash
# 将 web/ 目录内容复制到 server/src/main/resources/static/
cp -r web/* server/src/main/resources/static/

# 启动后端服务
java -jar server/target/server-*.jar

# 访问: http://localhost:8080
```

### 方式二：独立部署 (生产环境推荐)

前端和后端分别部署到不同的服务器：

#### 1. 启动后端服务
```bash
# 后端服务运行在 8080 端口
java -jar server/target/server-*.jar
```

#### 2. 启动前端服务
```bash
cd web/

# 方式 A: 使用 Node.js http-server
npm install -g http-server
http-server . -p 3000 --cors

# 方式 B: 使用 Python
python -m http.server 3000

# 方式 C: 使用启动脚本
./start-web.sh    # Linux/macOS
start-web.bat     # Windows
```

#### 3. 配置 API 地址
编辑 `web/js/config.js`：
```javascript
const CONFIG = {
    API_BASE_URL: 'http://your-backend-server:8080',
    // ...
};
```

### 方式三：Nginx 反向代理部署

#### Nginx 配置示例
```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    # 前端静态文件
    location / {
        root /var/www/lightscript-web;
        index index.html;
        try_files $uri $uri/ /index.html;
    }
    
    # 后端 API 代理
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    
    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

#### 部署步骤
```bash
# 1. 部署前端文件
sudo cp -r web/* /var/www/lightscript-web/

# 2. 配置 Nginx
sudo cp nginx.conf /etc/nginx/sites-available/lightscript
sudo ln -s /etc/nginx/sites-available/lightscript /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx

# 3. 启动后端服务
java -jar server/target/server-*.jar
```

## 跨域配置

后端已经配置了 CORS 支持，允许前端跨域访问：

```java
// CorsConfig.java
@Configuration
public class CorsConfig {
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        // ...
    }
}
```

## API 接口文档

### 认证接口
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/register` - 用户注册  
- `POST /api/auth/change-password` - 修改密码

### 管理接口
- `GET /api/web/dashboard/stats` - 仪表盘统计
- `GET /api/web/agents` - 客户端列表
- `GET /api/web/agents/{agentId}/tasks` - 客户端任务
- `GET /api/web/tasks` - 任务列表
- `GET /api/web/tasks/{taskId}` - 任务详情
- `GET /api/web/tasks/{taskId}/logs` - 任务日志
- `POST /api/web/tasks/create` - 创建任务
- `POST /api/web/tasks/batch` - 批量创建任务

### Agent 接口
- `POST /api/agent/register` - Agent 注册
- `POST /api/agent/heartbeat` - 心跳检测
- `GET /api/agent/tasks/pull` - 拉取任务
- `POST /api/agent/tasks/{taskId}/log` - 上报日志
- `POST /api/agent/tasks/{taskId}/finish` - 完成任务

## 开发调试

### 前端开发
```bash
cd web/
# 启动开发服务器
./start-web.sh

# 修改 config.js 指向后端地址
# 在浏览器中访问 http://localhost:3000
```

### 后端开发
```bash
# 启动后端服务 (支持热重载)
mvn spring-boot:run

# 或直接运行 jar 包
java -jar server/target/server-*.jar
```

### 联调测试
1. 启动后端服务: `http://localhost:8080`
2. 启动前端服务: `http://localhost:3000`  
3. 前端配置后端地址: `http://localhost:8080`
4. 测试 API 调用和页面功能

## 生产环境优化

### 前端优化
- 启用 Gzip 压缩
- 配置静态资源缓存
- 使用 CDN 加速
- 压缩 CSS/JS 文件

### 后端优化  
- 配置 JVM 参数
- 启用数据库连接池
- 配置日志级别
- 设置健康检查

### 安全配置
- 配置 HTTPS 证书
- 限制 CORS 来源
- 设置安全头
- 配置防火墙规则

## 监控和维护

### 前端监控
- 访问日志分析
- 页面性能监控
- 错误日志收集

### 后端监控
- 应用性能监控 (APM)
- 数据库性能监控
- 系统资源监控
- 业务指标监控

## 故障排除

### 常见问题
1. **跨域问题**: 检查 CORS 配置和前端 API 地址
2. **认证失败**: 检查 JWT 令牌和后端认证服务
3. **API 调用失败**: 检查网络连接和后端服务状态
4. **页面空白**: 检查前端 JavaScript 错误和资源加载

### 调试工具
- 浏览器开发者工具
- Postman API 测试
- Nginx 访问日志
- Spring Boot Actuator 监控

这种前后端分离的架构提供了更好的可维护性、可扩展性和部署灵活性，适合现代 Web 应用的开发和部署需求。
