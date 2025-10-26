# LightScript SPA重构规划

## 📋 目标

将现有的多页面应用（MPA）重构为单页面应用（SPA），实现：
- ✅ 页面切换无刷新、无闪烁
- ✅ 更流畅的用户体验
- ✅ 更快的页面切换速度

---

## 🎯 技术方案

### 1. 使用Vue Router

**核心库**：
- Vue 3.x（已有）
- Vue Router 4.x（需引入）
- Element Plus（已有）

**架构调整**：
```
原架构（MPA）：
- index.html
- agents.html
- tasks.html
- dashboard.html
- scripts.html

新架构（SPA）：
- index.html（单一入口）
- js/router.js（路由配置）
- js/views/（页面组件）
  - Dashboard.js
  - Agents.js
  - Tasks.js
  - Scripts.js
```

---

## 📁 文件结构规划

```
web/
├── index.html              # 单页应用入口
├── lib/                    # 第三方库（已完成）
│   ├── vue/
│   ├── vue-router/        # 需下载
│   ├── element-plus/
│   └── axios/
├── js/
│   ├── app.js             # Vue应用主文件
│   ├── router.js          # 路由配置
│   ├── common.js          # 公共函数（保留）
│   └── views/             # 页面组件
│       ├── Dashboard.js
│       ├── Agents.js
│       ├── Tasks.js
│       └── Scripts.js
└── css/
    └── common.css         # 公共样式（保留）
```

---

## 🔄 重构步骤

### 阶段1：准备工作（1小时）

1. **下载Vue Router**
   ```bash
   # 下载 vue-router
   https://cdn.jsdelivr.net/npm/vue-router@4/dist/vue-router.global.prod.js
   # 保存到: lib/vue-router/vue-router.global.prod.js
   ```

2. **创建路由配置**
   - 创建 `js/router.js`
   - 定义路由规则

3. **创建主应用文件**
   - 创建 `js/app.js`
   - 初始化Vue应用和路由

### 阶段2：组件化（2-3小时）

1. **转换Dashboard页面**
   - 将 `dashboard.html` 内容转为组件
   - 创建 `js/views/Dashboard.js`

2. **转换Agents页面**
   - 将 `agents.html` 内容转为组件
   - 创建 `js/views/Agents.js`

3. **转换Tasks页面**
   - 将 `tasks.html` 内容转为组件
   - 创建 `js/views/Tasks.js`

4. **转换Scripts页面**
   - 将 `scripts.html` 内容转为组件
   - 创建 `js/views/Scripts.js`

### 阶段3：集成测试（1小时）

1. **功能测试**
   - 导航切换
   - 数据加载
   - API调用

2. **性能测试**
   - 页面切换速度
   - 内存占用
   - 浏览器兼容性

3. **修复问题**

---

## 📝 路由配置示例

```javascript
// js/router.js
const routes = [
    {
        path: '/',
        redirect: '/dashboard'
    },
    {
        path: '/dashboard',
        name: 'Dashboard',
        component: Dashboard
    },
    {
        path: '/agents',
        name: 'Agents',
        component: Agents
    },
    {
        path: '/tasks',
        name: 'Tasks',
        component: Tasks
    },
    {
        path: '/scripts',
        name: 'Scripts',
        component: Scripts
    }
];

const router = VueRouter.createRouter({
    history: VueRouter.createWebHashHistory(),
    routes
});
```

---

## 📝 单页应用入口示例

```html
<!-- index.html -->
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>LightScript 管理平台</title>
    <script src="lib/vue/vue.global.prod.js"></script>
    <script src="lib/vue-router/vue-router.global.prod.js"></script>
    <script src="lib/element-plus/index.full.min.js"></script>
    <script src="lib/axios/axios.min.js"></script>
    <link rel="stylesheet" href="lib/element-plus/index.min.css">
    <link rel="stylesheet" href="css/common.css">
</head>
<body>
    <div id="app">
        <!-- 导航栏 -->
        <div class="navbar">
            <div class="navbar-brand">
                <h1>LightScript 管理平台</h1>
            </div>
            <div class="navbar-nav">
                <router-link to="/dashboard" class="nav-link">仪表盘</router-link>
                <router-link to="/agents" class="nav-link">客户端管理</router-link>
                <router-link to="/tasks" class="nav-link">任务管理</router-link>
                <router-link to="/scripts" class="nav-link">脚本管理</router-link>
            </div>
        </div>
        
        <!-- 路由视图 -->
        <router-view></router-view>
    </div>
    
    <script src="js/common.js"></script>
    <script src="js/router.js"></script>
    <script src="js/app.js"></script>
</body>
</html>
```

---

## ⚠️ 注意事项

### 1. 兼容性
- 保留现有HTML文件作为备份
- 可以同时支持MPA和SPA两种模式

### 2. 数据管理
- 考虑使用Vuex或Pinia做状态管理
- 统一管理API请求

### 3. SEO问题
- SPA对SEO不友好
- 如果需要SEO，考虑SSR或保留MPA

### 4. 浏览器历史
- 使用HTML5 History模式或Hash模式
- Hash模式更简单（推荐）

---

## 📊 预期效果

| 指标 | 当前（MPA） | 重构后（SPA） | 提升 |
|------|------------|--------------|------|
| 页面切换 | 0.5秒（刷新） | **0.1秒（无刷新）** | 5倍 ⚡ |
| 用户体验 | 有闪烁 | **完全平滑** | 质的飞跃 ⚡ |
| 代码复用 | 每页独立 | **组件复用** | 更易维护 ⚡ |
| 内存占用 | 每次重新加载 | **保持在内存** | 更高效 ⚡ |

---

## 🎯 预计时间

- **准备工作**: 1小时
- **组件化**: 2-3小时
- **测试调试**: 1小时
- **总计**: 4-5小时

---

## 📚 参考资源

- Vue Router官方文档: https://router.vuejs.org/
- Vue 3组件文档: https://vuejs.org/guide/components/
- Element Plus文档: https://element-plus.org/

---

## 💡 建议

1. **选择合适的时机**：在系统稳定运行后再重构
2. **分步实施**：先重构一个页面，测试无误后再继续
3. **备份现有代码**：重构前做好版本控制
4. **逐步迁移**：可以让MPA和SPA共存一段时间

---

**创建时间**: 2025-10-26
**当前状态**: 规划阶段
**优先级**: 中等
