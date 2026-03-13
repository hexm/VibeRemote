# LightScript 门户网站

这是 LightScript 产品的官方门户网站，采用现代化设计，参考了 aider.chat 的设计风格。

## 特性

- 🎨 现代化响应式设计
- 🚀 快速加载和流畅动画
- 📱 移动端友好
- ♿ 无障碍访问支持
- 🎯 SEO 优化

## 文件结构

```
portal/
├── index.html          # 主页面
├── styles.css          # 样式文件
├── script.js           # JavaScript 功能
├── assets/
│   └── logo.svg        # Logo 文件
└── README.md           # 说明文档
```

## 主要功能

### 1. 响应式导航栏
- 固定顶部导航
- 滚动时背景模糊效果
- 移动端汉堡菜单

### 2. 英雄区域
- 渐变背景
- 3D 终端演示窗口
- 动态打字效果

### 3. 功能特性展示
- 6个核心功能卡片
- 悬停动画效果
- 图标和描述

### 4. 使用场景
- 4个主要应用场景
- 简洁的图标展示

### 5. 快速开始
- 安装步骤指南
- 代码示例
- 一键复制功能

### 6. 页脚
- 完整的链接导航
- 社交媒体链接
- 版权信息

## 技术特点

### CSS 特性
- CSS Grid 和 Flexbox 布局
- CSS 自定义属性（变量）
- 现代动画和过渡效果
- 响应式设计

### JavaScript 功能
- 滚动监听和导航栏效果
- 平滑滚动到锚点
- 代码复制功能
- 终端动画效果
- 交叉观察器 API
- 懒加载图片

### 设计系统
- 一致的颜色方案
- 统一的间距和圆角
- 阴影和深度效果
- 可访问性考虑

## 部署说明

### 1. 静态部署
直接将 `portal` 目录部署到任何静态文件服务器：

```bash
# 使用 Python 简单服务器
cd portal
python -m http.server 8000

# 使用 Node.js serve
npx serve portal

# 使用 Nginx
# 将文件复制到 /var/www/html/
```

### 2. 集成到现有项目
将门户网站集成到 LightScript 项目中：

```bash
# 复制文件到 web-modern 目录
cp -r portal/* web-modern/public/

# 或者作为独立的门户站点
mkdir lightscript-portal
cp -r portal/* lightscript-portal/
```

### 3. 自定义配置

#### 修改品牌信息
编辑 `index.html` 中的：
- 页面标题和描述
- Logo 和品牌名称
- 联系信息

#### 调整样式
编辑 `styles.css` 中的 CSS 变量：
```css
:root {
    --primary-color: #3b82f6;    /* 主色调 */
    --secondary-color: #64748b;  /* 次要颜色 */
    /* ... 其他变量 */
}
```

#### 添加功能
在 `script.js` 中添加自定义 JavaScript 功能。

## 浏览器支持

- Chrome 60+
- Firefox 60+
- Safari 12+
- Edge 79+

## 性能优化

- 图片懒加载
- CSS 和 JS 压缩
- 字体预加载
- 关键 CSS 内联

## 无障碍访问

- 语义化 HTML 结构
- 键盘导航支持
- 屏幕阅读器友好
- 适当的对比度

## 后续改进

1. 添加多语言支持
2. 集成分析工具
3. 添加博客功能
4. 用户反馈系统
5. 在线文档集成

## 许可证

与 LightScript 项目使用相同的许可证。