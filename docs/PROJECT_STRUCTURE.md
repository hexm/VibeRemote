# LightScript 项目结构说明

## 📁 目录结构

```
LightScript/
├── agent/                          # Agent客户端模块
│   ├── src/                       # 源代码
│   ├── target/                    # 构建输出
│   └── pom.xml                    # Maven配置
│
├── server/                         # Server服务端模块
│   ├── src/                       # 源代码
│   │   ├── main/
│   │   │   ├── java/             # Java源码
│   │   │   └── resources/        # 配置文件
│   │   │       ├── application.yml           # 主配置
│   │   │       ├── application-dev.yml       # 开发环境配置
│   │   │       └── application-prod.yml      # 生产环境配置
│   │   └── test/                 # 测试代码
│   ├── logs/                      # 服务端日志目录
│   ├── target/                    # 构建输出
│   └── pom.xml                    # Maven配置
│
├── web/                            # 旧版前端（Vue2 + Element Plus）
│   ├── css/                       # 样式文件
│   ├── js/                        # JavaScript文件
│   ├── lib/                       # 第三方库
│   └── *.html                     # HTML页面
│
├── web-modern/                     # 现代化前端（React + Ant Design）
│   ├── src/                       # 源代码
│   │   ├── components/           # React组件
│   │   ├── pages/                # 页面组件
│   │   ├── services/             # API服务
│   │   ├── App.jsx               # 主应用
│   │   ├── main.jsx              # 入口文件
│   │   └── index.css             # 全局样式
│   ├── public/                    # 静态资源
│   ├── dist/                      # 构建输出
│   ├── package.json               # npm配置
│   ├── vite.config.js            # Vite配置
│   └── tailwind.config.js        # Tailwind配置
│
├── scripts/                        # 脚本目录
│   ├── bat/                       # Windows批处理脚本
│   │   ├── build.bat             # 构建脚本
│   │   ├── quick-start.bat       # 快速启动
│   │   ├── start-server.bat      # 启动服务端
│   │   ├── start-agent.bat       # 启动客户端
│   │   ├── start-web.bat         # 启动前端
│   │   └── reset-agent-id.bat    # 重置Agent ID
│   └── mac/                       # macOS Shell脚本
│       ├── build.sh              # 构建脚本
│       ├── quick-start.sh        # 快速启动
│       ├── start-server.sh       # 启动服务端
│       ├── start-agent.sh        # 启动客户端
│       ├── start-web.sh          # 启动旧版前端
│       ├── start-modern-web.sh   # 启动现代化前端
│       ├── start-all.sh          # 启动所有服务（旧版前端）
│       ├── start-all-modern.sh   # 启动所有服务（现代化前端）
│       ├── stop-all.sh           # 停止所有服务
│       ├── setup-ssh-key.sh      # 配置SSH免密登录
│       ├── deploy-to-aliyun.sh   # 阿里云部署脚本
│       └── README_DEPLOY.md      # 部署脚本说明
│
├── docs/                           # 文档目录
│   ├── PROJECT_SUMMARY.md         # 项目总结
│   ├── PROJECT_STRUCTURE.md       # 项目结构说明（本文件）
│   ├── QUICK_START.md             # 快速开始指南
│   ├── QUICK_DEPLOY.md            # 快速部署指南
│   ├── DEPLOYMENT_GUIDE.md        # 部署指南
│   ├── DEPLOYMENT_ALIYUN.md       # 阿里云部署详细文档
│   ├── DEPLOY_SUMMARY.md          # 部署总结
│   ├── FRONTEND_UPGRADE_GUIDE.md  # 前端升级指南
│   ├── MACOS_SETUP_GUIDE.md       # macOS环境配置
│   ├── MACOS_STARTUP_GUIDE.md     # macOS启动指南
│   ├── MANUAL_INSTALL_GUIDE.md    # 手动安装指南
│   ├── H2_PERSISTENCE_SETUP.md    # H2数据库持久化配置
│   ├── 功能设计文档.md            # 功能设计
│   ├── 需求分析文档.md            # 需求分析
│   ├── 详细设计文档.md            # 详细设计
│   ├── 产品方向.md                # 产品方向
│   ├── 批量任务功能-实施指南.md   # 批量任务实施
│   ├── 批量任务功能优化说明.md    # 批量任务优化
│   ├── 保活机制实现说明.md        # 保活机制
│   ├── 日志存储升级说明.md        # 日志存储
│   ├── 服务端恢复支持说明.md      # 服务端恢复
│   └── 升级后启动指南.md          # 升级启动
│
├── data/                           # 数据目录
│   └── lightscript.mv.db          # H2数据库文件
│
├── logs/                           # 日志目录
│   ├── lightscript-business.log   # 业务日志
│   ├── lightscript-server.log     # 服务端日志
│   ├── lightscript-server-error.log # 错误日志
│   └── tasks/                     # 任务日志
│       └── YYYY/MM/               # 按年月分类
│
├── pom.xml                         # Maven父项目配置
├── README.md                       # 项目说明
├── .gitignore                      # Git忽略配置
└── 功能清单.txt                    # 功能清单

```

## 📝 关键文件说明

### 配置文件
- `application.yml` - 主配置文件，包含通用配置
- `application-dev.yml` - 开发环境配置（内存数据库）
- `application-prod.yml` - 生产环境配置（持久化数据库）

### 启动脚本
- **Windows**: 使用 `scripts/bat/` 下的 `.bat` 文件
- **macOS/Linux**: 使用 `scripts/mac/` 下的 `.sh` 文件

### 前端项目
- **旧版**: `web/` - Vue2 + Element Plus，使用CDN加载
- **现代化**: `web-modern/` - React + Ant Design + Vite，推荐使用

### 日志文件
- **服务端日志**: `server/logs/server.log`
- **业务日志**: `logs/lightscript-business.log`
- **任务日志**: `logs/tasks/YYYY/MM/`

## 🚀 快速开始

### 开发环境
```bash
# macOS
./scripts/mac/quick-start.sh

# Windows
scripts\bat\quick-start.bat
```

### 生产部署
```bash
# 阿里云部署
./scripts/mac/deploy-to-aliyun.sh
```

## 📚 文档索引

- **快速开始**: [docs/QUICK_START.md](./QUICK_START.md)
- **部署指南**: [docs/DEPLOYMENT_ALIYUN.md](./DEPLOYMENT_ALIYUN.md)
- **前端升级**: [docs/FRONTEND_UPGRADE_GUIDE.md](./FRONTEND_UPGRADE_GUIDE.md)
- **功能设计**: [docs/功能设计文档.md](./功能设计文档.md)

## 🔧 开发工具

### 后端
- Java 11+
- Maven 3.6+
- Spring Boot 2.7.x
- H2 Database

### 前端
- Node.js 16+
- React 18
- Ant Design 5
- Vite 5
- Tailwind CSS 3

## 📦 构建输出

### 后端
- `server/target/server-*.jar` - 服务端可执行JAR
- `agent/target/agent-*-jar-with-dependencies.jar` - 客户端可执行JAR

### 前端
- `web-modern/dist/` - 前端构建输出（生产环境）

## 🗄️ 数据存储

### 开发环境
- 内存数据库（H2 in-memory）
- 每次重启数据清空

### 生产环境
- 持久化数据库（H2 file-based）
- 数据文件: `data/lightscript.mv.db`
- 自动备份机制

## 🔐 安全配置

### JWT认证
- 密钥配置在 `application.yml` 中
- 生产环境请修改默认密钥

### 默认账号
- 管理员: admin / admin123
- 普通用户: user / user123

**⚠️ 生产环境请立即修改默认密码！**

## 📞 技术支持

如有问题，请查看相关文档或检查日志文件。
