# 前端库文件说明

本目录用于存放前端依赖库文件，避免依赖外部CDN。

## 如何获取库文件

### 方法1：使用Python脚本自动下载（推荐）

```bash
cd web
python download-libs.py
```

### 方法2：手动下载

请从以下链接下载文件并放置到对应目录：

#### Vue.js
- 下载：https://cdn.jsdelivr.net/npm/vue@3/dist/vue.global.prod.js
- 保存到：`lib/vue/vue.global.prod.js`

#### Element Plus
- JS：https://cdn.jsdelivr.net/npm/element-plus@2/dist/index.full.min.js
  - 保存到：`lib/element-plus/index.full.min.js`
- CSS：https://cdn.jsdelivr.net/npm/element-plus@2/dist/index.min.css
  - 保存到：`lib/element-plus/index.min.css`

#### Axios
- 下载：https://cdn.jsdelivr.net/npm/axios@1/dist/axios.min.js
- 保存到：`lib/axios/axios.min.js`

#### ECharts
- 下载：https://cdn.jsdelivr.net/npm/echarts@5/dist/echarts.min.js
- 保存到：`lib/echarts/echarts.min.js`

### 方法3：使用浏览器直接下载

1. 在浏览器中打开上述链接
2. 右键 → 另存为
3. 保存到对应目录

## 目录结构

下载完成后，目录结构应该如下：

```
lib/
├── vue/
│   └── vue.global.prod.js
├── element-plus/
│   ├── index.full.min.js
│   └── index.min.css
├── axios/
│   └── axios.min.js
└── echarts/
    └── echarts.min.js
```

## 文件大小参考

- Vue.js: ~150KB
- Element Plus JS: ~2MB
- Element Plus CSS: ~250KB
- Axios: ~15KB
- ECharts: ~900KB

总大小约：3.3MB
