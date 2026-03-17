# LightScript Web 前端

这是 LightScript 的现代化前端界面，采用最新的前端技术栈，提供美观、流畅的用户体验。

## 🎨 技术栈

- **React 18** - 现代化的前端框架
- **Ant Design 5** - 企业级UI设计语言和组件库
- **Tailwind CSS** - 实用优先的CSS框架
- **Vite** - 下一代前端构建工具
- **Recharts** - 基于React的图表库
- **Lucide React** - 美观的图标库

## ✨ 特性

### 🎯 现代化设计
- 采用最新的设计趋势和视觉效果
- 响应式布局，完美适配各种设备
- 流畅的动画和过渡效果
- 深色/浅色主题支持

### 🚀 性能优化
- 基于Vite的快速构建和热重载
- 组件懒加载和代码分割
- 优化的打包体积
- 现代浏览器优化

### 💡 用户体验
- 直观的导航和布局
- 丰富的交互反馈
- 实时数据更新
- 优雅的错误处理

### 📱 移动端友好
- 完全响应式设计
- 触摸友好的交互
- 移动端优化的组件
- PWA支持（可扩展）

## 🚀 快速开始

### 环境要求
- Node.js 16+
- npm 8+

### 安装和启动

```bash
# 进入前端目录
cd web

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 或使用启动脚本
./start-web.sh
```

### 访问地址
- 开发服务器: http://localhost:3001
- 生产构建: `npm run build`

## 📁 项目结构

```
web/
├── public/                 # 静态资源
├── src/
│   ├── components/         # 可复用组件
│   │   └── Layout/        # 布局组件
│   ├── pages/             # 页面组件
│   │   ├── Dashboard.jsx  # 仪表盘
│   │   ├── Agents.jsx     # 客户端管理
│   │   ├── Tasks.jsx      # 任务管理
│   │   ├── Scripts.jsx    # 脚本管理
│   │   └── Login.jsx      # 登录页面
│   ├── services/          # API服务
│   ├── utils/             # 工具函数
│   ├── App.jsx           # 主应用组件
│   ├── main.jsx          # 应用入口
│   └── index.css         # 全局样式
├── package.json          # 项目配置
├── vite.config.js        # Vite配置
├── tailwind.config.js    # Tailwind配置
└── README.md            # 说明文档
```

## 🎨 设计亮点

### 1. 登录页面
- 渐变背景和动态效果
- 玻璃拟态设计风格
- 流畅的表单验证
- 默认账号提示

### 2. 仪表盘
- 实时数据可视化
- 交互式图表
- 统计卡片动画
- 系统健康度监控

### 3. 客户端管理
- 实时状态显示
- 资源使用率可视化
- 智能搜索和过滤
- 批量操作支持

### 4. 任务管理
- 任务状态实时更新
- 进度条可视化
- 日志查看器
- 任务创建向导

### 5. 脚本管理
- 语法高亮显示
- 多语言支持
- 在线编辑器
- 版本控制（可扩展）

## 🔧 配置

### API配置
编辑 `src/services/auth.js` 中的API地址：

```javascript
const API_BASE_URL = 'http://localhost:8080/api'
```

### 主题配置
在 `src/main.jsx` 中自定义Ant Design主题：

```javascript
const theme = {
  token: {
    colorPrimary: '#3b82f6',
    borderRadius: 8,
  }
}
```

### Tailwind配置
在 `tailwind.config.js` 中自定义样式：

```javascript
theme: {
  extend: {
    colors: {
      primary: {
        500: '#3b82f6',
      }
    }
  }
}
```

## 📦 构建部署

### 开发环境
```bash
npm run dev
```

### 生产构建
```bash
npm run build
```

### 预览构建
```bash
npm run preview
```

### Docker部署
```dockerfile
FROM node:18-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY . .
RUN npm run build
EXPOSE 3001
CMD ["npm", "run", "preview"]
```

## 🔄 与传统版本对比

| 特性 | 传统方案 | 当前版本 |
|------|----------|----------|
| 框架 | Vue 3 + Element Plus | React 18 + Ant Design 5 |
| 样式 | 传统CSS | Tailwind CSS |
| 构建工具 | 无 | Vite |
| 设计风格 | 传统企业风格 | 现代化设计 |
| 动画效果 | 基础 | 丰富流畅 |
| 响应式 | 基础支持 | 完全响应式 |
| 性能 | 一般 | 高性能 |
| 开发体验 | 基础 | 现代化工具链 |

## 🛠️ 开发指南

### 添加新页面
1. 在 `src/pages/` 创建新组件
2. 在 `src/App.jsx` 添加路由
3. 在 `src/components/Layout/Sidebar.jsx` 添加菜单项

### 添加新组件
1. 在 `src/components/` 创建组件
2. 使用Ant Design组件和Tailwind样式
3. 遵循React Hooks最佳实践

### API集成
1. 在 `src/services/` 创建API服务
2. 使用axios进行HTTP请求
3. 实现错误处理和加载状态

## 🐛 故障排除

### 常见问题

1. **端口冲突**
   ```bash
   # 修改端口
   npm run dev -- --port 3002
   ```

2. **依赖安装失败**
   ```bash
   # 清理缓存
   npm cache clean --force
   rm -rf node_modules package-lock.json
   npm install
   ```

3. **构建失败**
   ```bash
   # 检查Node.js版本
   node --version  # 需要16+
   ```

## 🔮 未来规划

- [ ] PWA支持
- [ ] 深色主题
- [ ] 国际化支持
- [ ] 更多图表类型
- [ ] 实时通知
- [ ] 拖拽功能
- [ ] 移动端App

## 📄 许可证

MIT License

## 🤝 贡献

欢迎提交Issue和Pull Request来改进项目！

---

**注意**: 这是LightScript的主要前端界面，提供完整的管理功能和现代化的用户体验。