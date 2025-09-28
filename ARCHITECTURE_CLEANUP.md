# LightScript 架构清理说明

## 🗂️ 服务端static目录清理

### 问题描述
服务端的 `server/src/main/resources/static/` 目录包含了重复的前端文件，与项目的前后端分离架构不符。

### 具体问题

1. **重复内容**
   - `server/src/main/resources/static/index.html` - 旧版本的管理界面
   - `server/src/main/resources/static/app.js` - 旧版本的JavaScript代码
   - `web/` 目录已包含完整的、更新的前端代码

2. **架构冲突**
   - 项目采用前后端分离架构
   - 前端应独立部署在 `web/` 目录
   - 服务端只提供API接口，不应包含前端文件

3. **维护问题**
   - 两套前端代码容易造成混淆
   - 更新时需要同步维护
   - 违背单一数据源原则

### 解决方案

#### ✅ 已完成
1. **更新.gitignore** - 忽略static目录，防止未来误提交
2. **文档说明** - 创建本文档说明清理原因

#### 🔄 建议操作
```bash
# 删除多余的static目录
rm -rf server/src/main/resources/static/

# 或在Windows中
rmdir /s server\src\main\resources\static
```

### 正确的架构

```
lightScript/
├── server/                    # 后端服务 (仅API)
│   ├── src/main/java/        # Java源码
│   ├── src/main/resources/   # 配置文件 (无static)
│   │   ├── application.yml   # 应用配置
│   │   └── logback.xml      # 日志配置
│   └── pom.xml              # Maven配置
├── web/                      # 前端应用 (独立)
│   ├── index.html           # 主页
│   ├── login.html           # 登录页
│   ├── dashboard.html       # 仪表盘
│   ├── js/                  # JavaScript文件
│   └── css/                 # 样式文件
└── agent/                   # 客户端代理
```

### 访问方式

#### 开发环境
- **API服务**: http://localhost:8080/api/*
- **前端页面**: 直接打开 `web/index.html` 或使用HTTP服务器

#### 生产环境
- **后端**: 部署Spring Boot应用 (仅API)
- **前端**: 部署到Nginx/Apache等Web服务器
- **代理配置**: 前端通过反向代理访问后端API

### 配置更新

#### 后端CORS配置
确保后端支持跨域请求：
```java
@Configuration
public class CorsConfig {
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        return source;
    }
}
```

#### 前端API配置
更新前端API基础URL：
```javascript
// web/js/config.js
const API_BASE_URL = 'http://localhost:8080/api';
```

### 优势

1. **清晰分离** - 前后端职责明确
2. **独立部署** - 可以分别部署和扩展
3. **技术选择** - 前后端可以使用不同技术栈
4. **团队协作** - 前后端团队可以并行开发
5. **缓存策略** - 静态资源和API可以使用不同缓存策略

### 注意事项

1. **开发调试** - 需要启动两个服务 (后端API + 前端服务器)
2. **CORS配置** - 确保跨域请求正确配置
3. **路径配置** - 前端API请求路径需要包含完整URL
4. **部署配置** - 生产环境需要配置反向代理

---

**结论**: 删除 `server/src/main/resources/static/` 目录有助于保持架构清晰，避免代码重复，符合现代Web应用的最佳实践。
