# E2E自动化测试设置指南

## 概述

已为LightScript系统创建了完整的端到端（E2E）自动化测试框架，使用Playwright进行浏览器自动化测试。

---

## 已创建的文件

### 1. 测试框架文件
```
e2e-tests/
├── package.json              # 项目依赖配置
├── playwright.config.js      # Playwright配置
├── .gitignore               # Git忽略文件
├── README.md                # 详细使用文档
├── run-tests.sh             # 快速启动脚本
└── tests/
    └── user-management.spec.js  # 用户管理测试用例
```

### 2. 测试用例（7个）

#### 测试1: 编辑admin用户 - 验证权限正确加载
- 验证编辑admin用户时，16个权限全部显示为选中状态
- 这是修复的核心问题

#### 测试2: 快捷模板功能 - 管理员模板
- 验证点击"管理员"模板后，选中16个权限

#### 测试3: 快捷模板功能 - 操作员模板
- 验证点击"操作员"模板后，选中11个权限

#### 测试4: 快捷模板功能 - 只读模板
- 验证点击"只读"模板后，选中4个权限

#### 测试5: 类别全选/全不选功能
- 验证点击类别的"全选"按钮，该类别所有权限被选中
- 验证点击"全不选"按钮，该类别所有权限被取消

#### 测试6: 创建用户完整流程
- 验证完整的用户创建流程
- 验证用户出现在列表中
- 自动清理测试数据

#### 测试7: 编辑用户并修改权限
- 创建测试用户（只读4个权限）
- 编辑用户，改为操作员（11个权限）
- 验证权限正确保存
- 再次打开编辑，验证权限正确显示
- 自动清理测试数据

---

## 安装步骤

### 方法1: 使用快速启动脚本（推荐）

```bash
cd e2e-tests
./run-tests.sh
```

脚本会自动：
1. 检查并安装npm依赖
2. 检查并安装Playwright浏览器
3. 检查服务器连接
4. 运行测试

### 方法2: 手动安装

```bash
# 1. 进入测试目录
cd e2e-tests

# 2. 安装依赖
npm install

# 3. 安装Playwright浏览器
npx playwright install chromium

# 4. 运行测试
npm test
```

---

## 运行测试

### 1. 无头模式（默认）
```bash
npm test
```
或
```bash
./run-tests.sh
```

### 2. 有头模式（可以看到浏览器）
```bash
npm run test:headed
```
或
```bash
./run-tests.sh headed
```

### 3. 调试模式（逐步执行）
```bash
npm run test:debug
```
或
```bash
./run-tests.sh debug
```

### 4. UI模式（交互式）
```bash
npm run test:ui
```
或
```bash
./run-tests.sh ui
```

### 5. 查看测试报告
```bash
npm run report
```

---

## 测试结果

测试完成后，会生成以下文件：

### 1. HTML报告
```
playwright-report/index.html
```
打开此文件可以查看详细的测试报告，包括：
- 测试通过/失败统计
- 每个测试的执行时间
- 失败测试的详细信息
- 截图和视频

### 2. 截图
```
test-results/edit-admin-permissions.png      # 编辑admin权限
test-results/template-admin.png              # 管理员模板
test-results/template-operator.png           # 操作员模板
test-results/template-readonly.png           # 只读模板
test-results/category-select-all.png         # 类别全选
test-results/category-deselect-all.png       # 类别全不选
test-results/create-user-form.png            # 创建用户表单
test-results/edit-user-permissions.png       # 编辑用户权限
```

### 3. 视频（失败时）
```
test-results/*.webm
```

---

## 网络问题解决方案

如果遇到Playwright浏览器下载失败（如当前情况），有以下解决方案：

### 方案1: 使用代理
```bash
# 设置HTTP代理
export HTTP_PROXY=http://proxy.example.com:8080
export HTTPS_PROXY=http://proxy.example.com:8080

# 然后安装
npx playwright install chromium
```

### 方案2: 使用国内镜像
```bash
# 设置Playwright下载镜像
export PLAYWRIGHT_DOWNLOAD_HOST=https://npmmirror.com/mirrors/playwright/

# 然后安装
npx playwright install chromium
```

### 方案3: 手动下载
1. 访问 https://playwright.dev/docs/browsers
2. 下载对应平台的浏览器
3. 解压到指定目录

### 方案4: 使用已安装的Chrome
修改 `playwright.config.js`：
```javascript
use: {
  channel: 'chrome', // 使用系统已安装的Chrome
}
```

### 方案5: 稍后重试
网络问题可能是临时的，稍后重试：
```bash
npx playwright install chromium
```

---

## 当前状态

### ✅ 已完成
1. 创建了完整的测试框架
2. 编写了7个测试用例
3. 配置了测试环境
4. 创建了快速启动脚本
5. 编写了详细文档

### ⏳ 待完成
1. 安装Playwright浏览器（因网络问题暂停）
2. 运行测试验证功能

---

## 手动测试建议

在Playwright浏览器安装完成前，建议进行手动测试：

### 测试步骤

1. **访问系统**
   ```
   打开浏览器: http://8.138.114.34
   ```

2. **登录**
   ```
   用户名: admin
   密码: admin123
   ```

3. **测试编辑用户权限加载**
   ```
   1. 点击左侧菜单"用户管理"
   2. 找到admin用户，点击"编辑"按钮
   3. ✓ 检查：16个权限是否全部显示为选中状态
   ```

4. **测试快捷模板**
   ```
   1. 点击"创建用户"按钮
   2. 点击"管理员"模板按钮
   3. ✓ 检查：是否选中16个权限
   4. 点击"操作员"模板按钮
   5. ✓ 检查：是否选中11个权限
   6. 点击"只读"模板按钮
   7. ✓ 检查：是否选中4个权限
   ```

5. **测试类别全选/全不选**
   ```
   1. 在创建用户对话框中
   2. 点击"用户管理"类别的"全选"按钮
   3. ✓ 检查：该类别4个权限是否全部选中
   4. 再次点击"全不选"按钮
   5. ✓ 检查：该类别4个权限是否全部取消
   ```

---

## API测试结果

虽然浏览器测试暂时无法运行，但我已经通过API测试验证了所有后端功能：

### ✅ 已验证的功能
1. 登录系统
2. 获取用户列表（2个用户）
3. 获取admin用户详情（包含16个权限）
4. 获取所有可用权限（16个权限，5个类别）
5. 创建用户（操作员模板，11个权限）
6. 验证权限保存正确
7. 更新用户权限（改为只读，4个权限）
8. 验证权限更新成功
9. 添加类别权限（从4个增加到8个）
10. 删除测试用户

### ✅ 前端资源验证
- 前端页面可访问（HTTP 200）
- JavaScript文件存在且可访问（1530 KB）
- CSS文件存在且可访问（21 KB）
- React根节点存在
- CORS配置正常

---

## 后续步骤

### 1. 解决网络问题
- 尝试使用代理或镜像
- 或等待网络恢复后重试

### 2. 运行自动化测试
```bash
cd e2e-tests
./run-tests.sh headed
```

### 3. 查看测试结果
```bash
npm run report
```

### 4. 根据测试结果修复问题（如有）

---

## 扩展测试

测试框架已经搭建完成，可以轻松添加更多测试：

### Agent分组测试
```javascript
// e2e-tests/tests/agent-groups.spec.js
test.describe('Agent分组功能测试', () => {
  test('创建分组', async ({ page }) => {
    // 测试代码
  });
});
```

### 任务管理测试
```javascript
// e2e-tests/tests/task-management.spec.js
test.describe('任务管理功能测试', () => {
  test('创建任务', async ({ page }) => {
    // 测试代码
  });
});
```

---

## 参考文档

- [Playwright官方文档](https://playwright.dev/)
- [测试框架README](../e2e-tests/README.md)
- [用户管理测试用例](../e2e-tests/tests/user-management.spec.js)

---

## 总结

已经为LightScript系统创建了完整的E2E自动化测试框架，包括：

✅ 测试框架配置  
✅ 7个详细的测试用例  
✅ 快速启动脚本  
✅ 完整的文档  
⏳ 等待浏览器安装完成后即可运行

所有后端功能已通过API测试验证正常，前端资源也已确认可访问。一旦Playwright浏览器安装完成，即可运行完整的UI自动化测试。

---

**文档创建时间**: 2026-03-01  
**测试框架**: Playwright  
**测试环境**: http://8.138.114.34  
**状态**: 框架已就绪，等待浏览器安装
