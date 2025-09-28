# LightScript Web 前端

LightScript 管理平台的前端界面，基于 Vue.js 3 开发。

## 技术栈

- **Vue.js 3**: 渐进式 JavaScript 框架
- **Element Plus**: Vue 3 UI 组件库
- **Axios**: HTTP 客户端
- **原生 JavaScript**: 无需复杂构建工具

## 项目结构

```
web/
├── index.html          # 主页面
├── css/
│   └── style.css      # 样式文件
├── js/
│   ├── config.js      # 配置文件
│   └── app.js         # 主应用逻辑
├── package.json       # 项目配置
└── README.md         # 说明文档
```

## 功能特性

### 🔐 用户认证
- JWT 令牌登录
- 自动登录状态检测
- 安全退出登录

### 📊 仪表盘
- 系统概览统计
- 实时数据展示
- 响应式卡片布局

### 🖥️ 客户端管理
- 客户端列表查看
- 在线/离线状态显示
- 单个客户端任务管理
- 实时状态更新

### 📋 任务管理
- 任务列表查看
- 批量脚本下发
- 任务日志查看
- 任务状态跟踪

## 快速开始

### 方式一：直接运行（推荐）
```bash
# 直接用浏览器打开 index.html
# 或使用简单的 HTTP 服务器
python -m http.server 3000
# 或
npx http-server . -p 3000
```

### 方式二：使用 Node.js
```bash
# 安装依赖（可选）
npm install

# 启动开发服务器
npm run dev
```

### 配置后端地址
编辑 `js/config.js` 文件中的 API 地址：
```javascript
const CONFIG = {
    API_BASE_URL: 'http://your-backend-server:8080',
    // ...
};
```

## 开发指南

### 添加新页面
1. 在 `index.html` 中添加新的菜单项
2. 在 `app.js` 中添加对应的数据和方法
3. 在 `css/style.css` 中添加样式

### 添加新 API
1. 在 `config.js` 中定义 API 端点
2. 在 `app.js` 中创建调用方法
3. 在组件中使用新方法

### 自定义样式
- 主要样式在 `css/style.css` 中
- 支持响应式设计
- 使用 CSS Grid 和 Flexbox 布局

## API 配置

### 认证相关
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/register` - 用户注册
- `POST /api/auth/change-password` - 修改密码

### 管理相关
- `GET /api/web/dashboard/stats` - 仪表盘统计
- `GET /api/web/agents` - 客户端列表
- `GET /api/web/tasks` - 任务列表
- `POST /api/web/tasks/create` - 创建任务
- `POST /api/web/tasks/batch` - 批量创建任务

## 部署说明

### 开发环境
直接在后端 `static` 目录中运行，Spring Boot 自动提供静态文件服务。

### 生产环境
可以部署到独立的 Web 服务器：

#### Nginx 配置
```nginx
server {
    listen 80;
    server_name your-frontend-domain.com;
    root /var/www/lightscript-web;
    index index.html;
    
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    # API 代理到后端
    location /api/ {
        proxy_pass http://backend-server:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

#### Apache 配置
```apache
<VirtualHost *:80>
    ServerName your-frontend-domain.com
    DocumentRoot /var/www/lightscript-web
    
    <Directory /var/www/lightscript-web>
        Options Indexes FollowSymLinks
        AllowOverride All
        Require all granted
    </Directory>
    
    # API 代理
    ProxyPass /api/ http://backend-server:8080/api/
    ProxyPassReverse /api/ http://backend-server:8080/api/
</VirtualHost>
```

## 浏览器兼容性

- Chrome 60+
- Firefox 55+
- Safari 12+
- Edge 79+

## 性能优化

### 已实现的优化
- 组件懒加载
- 图片压缩
- CSS 压缩
- 请求防抖和节流
- 本地存储缓存

### 建议的优化
- 使用 CDN 加速静态资源
- 启用 Gzip 压缩
- 添加 Service Worker 缓存
- 图片懒加载

## 故障排除

### 常见问题

1. **无法连接后端**
   - 检查 `config.js` 中的 API 地址
   - 确认后端服务正常运行
   - 检查跨域配置

2. **登录失败**
   - 检查用户名密码
   - 查看浏览器控制台错误
   - 确认后端认证服务正常

3. **页面空白**
   - 检查浏览器控制台错误
   - 确认 JavaScript 文件加载正常
   - 检查 Vue.js 和 Element Plus 版本

### 调试技巧
- 打开浏览器开发者工具
- 查看 Network 标签页的请求
- 查看 Console 标签页的错误信息
- 使用 Vue DevTools 调试组件状态

## 更新日志

### v1.0.0
- 初始版本发布
- 实现基础管理功能
- 支持用户认证和权限管理
- 完成客户端和任务管理界面
