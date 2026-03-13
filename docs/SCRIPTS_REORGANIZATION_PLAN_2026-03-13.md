# Scripts目录重组计划

## 文档信息
- **创建时间**: 2026-03-13
- **版本**: 1.0
- **状态**: 规划中
- **作者**: 系统架构师

## 1. 当前Scripts目录分析

### 1.1 scripts/mac/ 目录内容分析

#### Server相关脚本 (迁移到 server/scripts/)
- `start-server.sh` - Server启动脚本 ✅ 迁移
- `migrate-to-mysql.sh` - 数据库迁移脚本 ✅ 迁移
- `configure-mysql-aliyun.sh` - MySQL配置脚本 ✅ 迁移
- `install-mysql-aliyun.sh` - MySQL安装脚本 ✅ 迁移
- `setup-mysql-aliyun.sh` - MySQL设置脚本 ✅ 迁移
- `start-with-mysql.sh` - MySQL启动脚本 ✅ 迁移

#### Agent相关脚本 (保留在scripts/或删除)
- `start-agent.sh` - Agent启动脚本 ❌ 删除 (已有agent/start-agent.sh)
- `start-agent-aliyun.sh` - 阿里云Agent启动 ❌ 删除 (过时)
- `reset-agent-id.sh` - 重置Agent ID ⚠️ 评估保留

#### 部署相关脚本 (迁移到 server/scripts/)
- `deploy-to-aliyun.sh` - 阿里云部署脚本 ✅ 迁移
- `setup-ssh-key.sh` - SSH密钥设置 ✅ 迁移
- `test-aliyun.sh` - 阿里云测试脚本 ✅ 迁移

#### 通用构建脚本 (保留在scripts/)
- `build.sh` - 通用构建脚本 ✅ 保留
- `install-dependencies.sh` - 依赖安装脚本 ✅ 保留
- `setup-idea-maven.sh` - IDE设置脚本 ✅ 保留
- `setup-portable-env.sh` - 便携环境设置 ✅ 保留

#### Web相关脚本 (评估)
- `start-web.sh` - 旧版Web启动 ❌ 删除 (已有web-modern)
- `start-modern-web.sh` - 现代Web启动 ✅ 保留
- `start-all.sh` - 启动所有服务 ✅ 保留
- `start-all-modern.sh` - 启动现代版本 ✅ 保留
- `stop-all.sh` - 停止所有服务 ✅ 保留

#### 测试脚本 (评估)
- `test-all.sh` - 全面测试脚本 ✅ 保留
- `test-manual-start.sh` - 手动启动测试 ❌ 删除 (过时)
- `test-multi-target.sh` - 多目标测试 ❌ 删除 (过时)
- `test-user-agent-groups.sh` - 用户组测试 ❌ 删除 (过时)

#### 快速启动脚本
- `quick-start.sh` - 快速启动脚本 ✅ 保留

#### 文档
- `README.md` - 脚本说明文档 ✅ 保留
- `README_DEPLOY.md` - 部署说明文档 ✅ 迁移到server/scripts/

### 1.2 scripts/bat/ 目录内容分析

#### Windows脚本 (对应处理)
- `build.bat` - Windows构建脚本 ✅ 保留
- `quick-start.bat` - Windows快速启动 ✅ 保留
- `start-server.bat` - Windows Server启动 ✅ 迁移到server/scripts/
- `start-agent.bat` - Windows Agent启动 ❌ 删除 (已有agent/start-agent.bat)
- `start-web.bat` - Windows Web启动 ❌ 删除 (过时)
- `reset-agent-id.bat` - Windows重置Agent ID ⚠️ 评估保留

## 2. 重组计划

### 2.1 创建新的目录结构

```
server/
├── scripts/
│   ├── start-server.sh
│   ├── start-server.bat
│   ├── migrate-to-mysql.sh
│   ├── configure-mysql-aliyun.sh
│   ├── install-mysql-aliyun.sh
│   ├── setup-mysql-aliyun.sh
│   ├── start-with-mysql.sh
│   ├── deploy-to-aliyun.sh
│   ├── setup-ssh-key.sh
│   ├── test-aliyun.sh
│   └── README_DEPLOY.md
└── ...

scripts/
├── mac/
│   ├── build.sh
│   ├── install-dependencies.sh
│   ├── setup-idea-maven.sh
│   ├── setup-portable-env.sh
│   ├── start-modern-web.sh
│   ├── start-all.sh
│   ├── start-all-modern.sh
│   ├── stop-all.sh
│   ├── test-all.sh
│   ├── quick-start.sh
│   ├── reset-agent-id.sh (评估)
│   └── README.md
└── bat/
    ├── build.bat
    ├── quick-start.bat
    └── reset-agent-id.bat (评估)
```

### 2.2 迁移操作

#### 迁移到 server/scripts/
```bash
# Server相关脚本
mv scripts/mac/start-server.sh server/scripts/
mv scripts/mac/migrate-to-mysql.sh server/scripts/
mv scripts/mac/configure-mysql-aliyun.sh server/scripts/
mv scripts/mac/install-mysql-aliyun.sh server/scripts/
mv scripts/mac/setup-mysql-aliyun.sh server/scripts/
mv scripts/mac/start-with-mysql.sh server/scripts/

# 部署相关脚本
mv scripts/mac/deploy-to-aliyun.sh server/scripts/
mv scripts/mac/setup-ssh-key.sh server/scripts/
mv scripts/mac/test-aliyun.sh server/scripts/

# Windows Server脚本
mv scripts/bat/start-server.bat server/scripts/

# 文档
mv scripts/mac/README_DEPLOY.md server/scripts/
```

#### 删除过时脚本
```bash
# 过时的Agent脚本 (已有标准版本)
rm scripts/mac/start-agent.sh
rm scripts/mac/start-agent-aliyun.sh
rm scripts/bat/start-agent.bat

# 过时的Web脚本 (已有现代版本)
rm scripts/mac/start-web.sh
rm scripts/bat/start-web.bat

# 过时的测试脚本
rm scripts/mac/test-manual-start.sh
rm scripts/mac/test-multi-target.sh
rm scripts/mac/test-user-agent-groups.sh
rm -rf scripts/mac/test-results/
```

### 2.3 更新脚本路径引用

#### 需要更新的脚本
1. `scripts/mac/start-all.sh` - 更新server启动路径
2. `scripts/mac/start-all-modern.sh` - 更新server启动路径
3. `scripts/mac/stop-all.sh` - 更新停止逻辑
4. `scripts/mac/test-all.sh` - 更新测试路径

#### 路径更新示例
```bash
# 旧路径
./scripts/mac/start-server.sh

# 新路径
./server/scripts/start-server.sh
```

## 3. 实施步骤

### 3.1 第一阶段：创建目录和迁移文件
1. 创建 `server/scripts/` 目录
2. 迁移Server相关脚本
3. 迁移部署相关脚本
4. 迁移Windows脚本

### 3.2 第二阶段：删除过时文件
1. 删除重复的Agent脚本
2. 删除过时的Web脚本
3. 删除过时的测试脚本
4. 清理测试结果目录

### 3.3 第三阶段：更新引用
1. 更新启动脚本中的路径引用
2. 更新文档中的路径说明
3. 测试所有脚本功能

### 3.4 第四阶段：文档更新
1. 更新README文档
2. 创建新的脚本使用指南
3. 更新部署文档

## 4. 预期效果

### 4.1 目录结构优化
- **Server脚本集中**: 所有Server相关脚本在 `server/scripts/`
- **通用脚本保留**: 构建、环境设置等通用脚本保留在 `scripts/`
- **清理冗余**: 删除重复和过时的脚本

### 4.2 维护性提升
- **职责清晰**: 每个目录的脚本职责明确
- **易于查找**: 按功能模块组织脚本
- **减少混淆**: 避免重复脚本造成的混淆

### 4.3 部署简化
- **Server部署**: 只需关注 `server/scripts/` 目录
- **开发环境**: 使用 `scripts/` 目录的通用脚本
- **文档清晰**: 每个目录有对应的README文档

## 5. 风险评估

### 5.1 潜在风险
- **路径引用**: 现有脚本可能引用旧路径
- **CI/CD影响**: 自动化流程可能依赖旧路径
- **文档过时**: 文档中的路径说明需要更新

### 5.2 缓解措施
- **逐步迁移**: 分阶段进行，确保每步都可验证
- **保留备份**: 迁移前备份重要脚本
- **全面测试**: 迁移后测试所有功能
- **文档同步**: 及时更新相关文档

这个重组计划将使项目的脚本管理更加规范和高效。