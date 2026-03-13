# LightScript 门户网站设计文档

## 项目概述

为 LightScript 产品创建了一个现代化的门户网站，参考 aider.chat 的设计风格，提供产品介绍、功能展示和快速开始指南。

## 设计理念

### 1. 现代化设计
- 采用渐变背景和卡片式布局
- 使用现代字体 Inter
- 响应式设计，支持各种设备

### 2. 用户体验优先
- 清晰的信息层次结构
- 流畅的动画和过渡效果
- 直观的导航和交互

### 3. 技术先进性
- 使用现代 CSS 特性
- 原生 JavaScript，无依赖
- 性能优化和无障碍访问

## 技术架构

### 前端技术栈
- **HTML5**: 语义化标记
- **CSS3**: 现代样式特性
  - CSS Grid 和 Flexbox
  - CSS 自定义属性
  - 动画和过渡
- **JavaScript ES6+**: 现代 JavaScript 特性
  - 模块化代码
  - 异步操作
  - DOM 操作

### 设计系统

#### 颜色方案
```css
--primary-color: #3b82f6    /* 主蓝色 */
--primary-dark: #1d4ed8     /* 深蓝色 */
--secondary-color: #64748b  /* 灰蓝色 */
--accent-color: #06b6d4     /* 青色 */
--success-color: #10b981    /* 绿色 */
```

#### 间距系统
- 基础单位: 0.25rem (4px)
- 常用间距: 0.5rem, 1rem, 1.5rem, 2rem, 4rem, 6rem

#### 圆角系统
- 小: 0.375rem
- 中: 0.5rem  
- 大: 0.75rem
- 特大: 1rem

## 页面结构

### 1. 导航栏 (Navigation)
- 固定顶部导航
- 品牌 Logo 和名称
- 主要导航链接
- 行动按钮 (CTA)
- 移动端汉堡菜单

### 2. 英雄区域 (Hero Section)
- 主标题和副标题
- 产品价值主张
- 主要行动按钮
- 3D 终端演示窗口

### 3. 功能特性 (Features)
- 6个核心功能卡片
- 图标 + 标题 + 描述
- 悬停动画效果

### 4. 使用场景 (Use Cases)
- 4个主要应用场景
- 简洁的展示方式
- Emoji 图标

### 5. 快速开始 (Quick Start)
- 安装步骤指南
- 代码示例展示
- 一键复制功能

### 6. 页脚 (Footer)
- 品牌信息
- 链接导航
- 社交媒体
- 版权信息

## 核心功能

### 1. 响应式设计
```css
/* 平板设备 */
@media (max-width: 768px) {
    .hero .container {
        grid-template-columns: 1fr;
    }
}

/* 手机设备 */
@media (max-width: 480px) {
    .hero-title {
        font-size: 2rem;
    }
}
```

### 2. 动画效果
- 滚动触发动画
- 悬停状态变化
- 终端打字效果
- 平滑过渡

### 3. 交互功能
- 平滑滚动到锚点
- 代码复制功能
- 移动端菜单切换
- 滚动导航栏效果

### 4. 性能优化
- 图片懒加载
- CSS 和 JS 压缩
- 关键资源预加载
- 缓存策略

## 文件结构

```
portal/
├── index.html              # 主页面
├── styles.css              # 样式文件 (约 400 行)
├── script.js               # JavaScript 功能 (约 200 行)
├── assets/
│   └── logo.svg            # SVG Logo
├── deploy.sh               # 部署脚本
├── dev-server.sh           # 开发服务器
└── README.md               # 使用说明
```

## 部署方案

### 1. 静态部署
```bash
# 使用 Python 服务器
python -m http.server 8000

# 使用 Node.js serve
npx serve portal

# 使用 Nginx
cp -r portal/* /var/www/html/
```

### 2. 集成部署
```bash
# 集成到现有项目
cp -r portal/* web-modern/public/

# 作为子路径部署
# 访问地址: http://domain.com/portal/
```

### 3. CDN 部署
- 上传静态文件到 CDN
- 配置缓存策略
- 启用 Gzip 压缩

## 性能指标

### 页面加载性能
- **首次内容绘制 (FCP)**: < 1.5s
- **最大内容绘制 (LCP)**: < 2.5s
- **累积布局偏移 (CLS)**: < 0.1
- **首次输入延迟 (FID)**: < 100ms

### 文件大小
- HTML: ~15KB
- CSS: ~25KB
- JavaScript: ~8KB
- 总计: ~50KB (压缩前)

### 浏览器支持
- Chrome 60+
- Firefox 60+
- Safari 12+
- Edge 79+

## 无障碍访问

### WCAG 2.1 AA 标准
- 语义化 HTML 结构
- 适当的颜色对比度 (4.5:1)
- 键盘导航支持
- 屏幕阅读器友好
- 焦点指示器

### 实现细节
```html
<!-- 语义化标记 -->
<nav role="navigation" aria-label="主导航">
<main role="main">
<section aria-labelledby="features-title">

<!-- 键盘访问 -->
<button tabindex="0" aria-label="复制代码">
```

## SEO 优化

### 基础 SEO
```html
<title>LightScript - 轻量级脚本执行平台</title>
<meta name="description" content="现代化的分布式脚本执行平台">
<meta name="keywords" content="脚本执行,自动化,运维,分布式">
```

### 结构化数据
- Open Graph 标签
- Twitter Card 标签
- JSON-LD 结构化数据

### 技术 SEO
- 语义化 HTML
- 合理的标题层次
- 内部链接结构
- 网站地图

## 后续改进计划

### 短期 (1-2 周)
1. 添加更多动画效果
2. 优化移动端体验
3. 添加更多示例代码
4. 完善错误处理

### 中期 (1-2 月)
1. 多语言支持 (i18n)
2. 暗色主题切换
3. 在线文档集成
4. 用户反馈系统

### 长期 (3-6 月)
1. 博客功能
2. 社区论坛
3. 在线演示环境
4. 分析和监控

## 维护指南

### 内容更新
1. 编辑 `index.html` 更新文案
2. 修改 `styles.css` 调整样式
3. 更新 `assets/` 中的图片资源

### 样式定制
```css
/* 修改主色调 */
:root {
    --primary-color: #your-color;
}

/* 添加新的组件样式 */
.new-component {
    /* 样式规则 */
}
```

### 功能扩展
```javascript
// 添加新的交互功能
function newFeature() {
    // 功能实现
}

// 注册事件监听器
document.addEventListener('DOMContentLoaded', newFeature);
```

## 总结

LightScript 门户网站成功实现了：

1. **现代化设计**: 参考业界最佳实践，创建了专业的产品门户
2. **技术先进性**: 使用现代 Web 技术，确保性能和兼容性
3. **用户体验**: 注重交互细节和无障碍访问
4. **可维护性**: 清晰的代码结构和完善的文档

该门户网站为 LightScript 产品提供了专业的线上展示平台，有助于提升品牌形象和用户获取。