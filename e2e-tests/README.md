# LightScript E2E 自动化测试

使用 Playwright 进行端到端自动化测试。

## 安装

```bash
cd e2e-tests
npm install
npx playwright install chromium
```

## 运行测试

### 1. 运行所有测试（无头模式）
```bash
npm test
```

### 2. 运行测试（有头模式，可以看到浏览器）
```bash
npm run test:headed
```

### 3. 调试模式（逐步执行）
```bash
npm run test:debug
```

### 4. UI模式（交互式测试）
```bash
npm run test:ui
```

### 5. 查看测试报告
```bash
npm run report
```

## 测试用例

### 用户管理功能测试 (user-management.spec.js)

1. **测试1: 编辑admin用户 - 验证权限正确加载**
   - 验证编辑admin用户时，16个权限全部显示为选中状态
   - 截图: `test-results/edit-admin-permissions.png`

2. **测试2: 快捷模板功能 - 管理员模板**
   - 验证点击"管理员"模板后，选中16个权限
   - 截图: `test-results/template-admin.png`

3. **测试3: 快捷模板功能 - 操作员模板**
   - 验证点击"操作员"模板后，选中11个权限
   - 截图: `test-results/template-operator.png`

4. **测试4: 快捷模板功能 - 只读模板**
   - 验证点击"只读"模板后，选中4个权限
   - 截图: `test-results/template-readonly.png`

5. **测试5: 类别全选/全不选功能**
   - 验证点击类别的"全选"按钮，该类别所有权限被选中
   - 验证点击"全不选"按钮，该类别所有权限被取消
   - 截图: `test-results/category-select-all.png`, `test-results/category-deselect-all.png`

6. **测试6: 创建用户完整流程**
   - 验证完整的用户创建流程
   - 验证用户出现在列表中
   - 自动清理测试数据
   - 截图: `test-results/create-user-form.png`

7. **测试7: 编辑用户并修改权限**
   - 创建测试用户（只读4个权限）
   - 编辑用户，改为操作员（11个权限）
   - 验证权限正确保存
   - 再次打开编辑，验证权限正确显示
   - 自动清理测试数据
   - 截图: `test-results/edit-user-permissions.png`

## 测试配置

- **测试环境**: http://8.138.114.34
- **登录账号**: admin / admin123
- **浏览器**: Chromium
- **并发**: 1个worker（顺序执行）
- **失败重试**: 本地0次，CI环境2次
- **截图**: 失败时自动截图
- **视频**: 失败时保留视频

## 测试结果

测试结果保存在以下位置：
- **HTML报告**: `playwright-report/index.html`
- **截图**: `test-results/*.png`
- **视频**: `test-results/*.webm`
- **追踪**: `test-results/*.zip`

## 故障排查

### 问题1: 无法连接到服务器
```bash
# 检查服务器是否可访问
curl http://8.138.114.34

# 检查后端API
curl http://8.138.114.34:8080/actuator/health
```

### 问题2: 元素找不到
- 检查选择器是否正确
- 增加等待时间 `await page.waitForTimeout(1000)`
- 使用 `npm run test:debug` 调试模式查看

### 问题3: 测试超时
- 增加超时时间: `{ timeout: 10000 }`
- 检查网络连接
- 检查服务器响应速度

## 最佳实践

1. **每次测试前登录**
   - 使用 `beforeEach` 钩子自动登录

2. **清理测试数据**
   - 测试结束后删除创建的测试用户
   - 避免污染数据库

3. **截图保存**
   - 关键步骤截图，便于问题排查
   - 失败时自动截图

4. **等待策略**
   - 使用 `waitForSelector` 等待元素出现
   - 使用 `waitForLoadState` 等待页面加载
   - 必要时使用 `waitForTimeout` 等待动画完成

5. **断言清晰**
   - 使用有意义的断言消息
   - 验证关键数据

## 扩展测试

可以添加更多测试用例：

### Agent分组测试
```javascript
test.describe('Agent分组功能测试', () => {
  // 测试创建分组
  // 测试添加Agent到分组
  // 测试删除分组
});
```

### 任务管理测试
```javascript
test.describe('任务管理功能测试', () => {
  // 测试创建任务
  // 测试按分组选择Agent
  // 测试查看任务执行日志
});
```

## 持续集成

可以集成到CI/CD流程：

```yaml
# .github/workflows/e2e-tests.yml
name: E2E Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
      - run: cd e2e-tests && npm install
      - run: npx playwright install --with-deps chromium
      - run: npm test
      - uses: actions/upload-artifact@v3
        if: always()
        with:
          name: playwright-report
          path: e2e-tests/playwright-report/
```

## 参考文档

- [Playwright官方文档](https://playwright.dev/)
- [Playwright API](https://playwright.dev/docs/api/class-playwright)
- [最佳实践](https://playwright.dev/docs/best-practices)
