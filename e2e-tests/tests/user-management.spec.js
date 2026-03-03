const { test, expect } = require('@playwright/test');

// 测试配置
const BASE_URL = 'http://8.138.114.34';
const ADMIN_USERNAME = 'admin';
const ADMIN_PASSWORD = 'admin123';

test.describe('用户管理功能测试', () => {
  
  test.beforeEach(async ({ page }) => {
    // 访问登录页面
    await page.goto(BASE_URL);
    
    // 等待页面加载
    await page.waitForLoadState('networkidle');
    
    // 登录
    await page.fill('input[type="text"]', ADMIN_USERNAME);
    await page.fill('input[type="password"]', ADMIN_PASSWORD);
    await page.click('button[type="submit"]');
    
    // 等待登录成功，跳转到仪表盘
    await page.waitForURL('**/dashboard', { timeout: 10000 });
    
    // 导航到用户管理页面
    await page.click('text=用户管理');
    await page.waitForURL('**/users', { timeout: 5000 });
    
    // 等待用户列表加载
    await page.waitForSelector('table', { timeout: 5000 });
  });

  test('测试1: 编辑admin用户 - 验证权限正确加载', async ({ page }) => {
    console.log('开始测试: 编辑admin用户权限加载');
    
    // 找到admin用户行，点击编辑按钮
    const adminRow = page.locator('tr').filter({ hasText: 'admin' }).first();
    await adminRow.locator('button:has-text("编辑")').click();
    
    // 等待编辑对话框出现
    await page.waitForSelector('.ant-modal', { timeout: 5000 });
    await expect(page.locator('.ant-modal-title')).toContainText('编辑用户');
    
    // 等待权限复选框渲染
    await page.waitForTimeout(500);
    
    // 验证权限复选框是否存在
    const checkboxes = page.locator('.ant-checkbox-wrapper');
    const checkboxCount = await checkboxes.count();
    console.log(`找到 ${checkboxCount} 个权限复选框`);
    
    // 验证至少有16个权限复选框
    expect(checkboxCount).toBeGreaterThanOrEqual(16);
    
    // 验证选中的权限数量
    const checkedCheckboxes = page.locator('.ant-checkbox-wrapper .ant-checkbox-checked');
    const checkedCount = await checkedCheckboxes.count();
    console.log(`选中的权限数量: ${checkedCount}`);
    
    // admin应该有16个权限全部选中
    expect(checkedCount).toBe(16);
    
    // 截图保存
    await page.screenshot({ path: 'test-results/edit-admin-permissions.png', fullPage: true });
    
    console.log('✅ 测试通过: admin用户的16个权限全部正确显示为选中状态');
    
    // 关闭对话框
    await page.click('button:has-text("取消")');
  });

  test('测试2: 快捷模板功能 - 管理员模板', async ({ page }) => {
    console.log('开始测试: 快捷模板 - 管理员');
    
    // 点击创建用户按钮
    await page.click('button:has-text("创建用户")');
    
    // 等待创建对话框出现
    await page.waitForSelector('.ant-modal', { timeout: 5000 });
    await expect(page.locator('.ant-modal-title')).toContainText('创建用户');
    
    // 等待权限区域渲染
    await page.waitForTimeout(500);
    
    // 点击"管理员"模板按钮
    await page.click('button:has-text("管理员")');
    
    // 等待权限更新
    await page.waitForTimeout(300);
    
    // 验证选中的权限数量
    const checkedCheckboxes = page.locator('.ant-checkbox-wrapper .ant-checkbox-checked');
    const checkedCount = await checkedCheckboxes.count();
    console.log(`管理员模板选中的权限数量: ${checkedCount}`);
    
    // 应该选中16个权限
    expect(checkedCount).toBe(16);
    
    // 截图保存
    await page.screenshot({ path: 'test-results/template-admin.png', fullPage: true });
    
    console.log('✅ 测试通过: 管理员模板正确选中16个权限');
    
    // 关闭对话框
    await page.click('button:has-text("取消")');
  });

  test('测试3: 快捷模板功能 - 操作员模板', async ({ page }) => {
    console.log('开始测试: 快捷模板 - 操作员');
    
    // 点击创建用户按钮
    await page.click('button:has-text("创建用户")');
    
    // 等待创建对话框出现
    await page.waitForSelector('.ant-modal', { timeout: 5000 });
    
    // 等待权限区域渲染
    await page.waitForTimeout(500);
    
    // 点击"操作员"模板按钮
    await page.click('button:has-text("操作员")');
    
    // 等待权限更新
    await page.waitForTimeout(300);
    
    // 验证选中的权限数量
    const checkedCheckboxes = page.locator('.ant-checkbox-wrapper .ant-checkbox-checked');
    const checkedCount = await checkedCheckboxes.count();
    console.log(`操作员模板选中的权限数量: ${checkedCount}`);
    
    // 应该选中11个权限
    expect(checkedCount).toBe(11);
    
    // 截图保存
    await page.screenshot({ path: 'test-results/template-operator.png', fullPage: true });
    
    console.log('✅ 测试通过: 操作员模板正确选中11个权限');
    
    // 关闭对话框
    await page.click('button:has-text("取消")');
  });

  test('测试4: 快捷模板功能 - 只读模板', async ({ page }) => {
    console.log('开始测试: 快捷模板 - 只读');
    
    // 点击创建用户按钮
    await page.click('button:has-text("创建用户")');
    
    // 等待创建对话框出现
    await page.waitForSelector('.ant-modal', { timeout: 5000 });
    
    // 等待权限区域渲染
    await page.waitForTimeout(500);
    
    // 点击"只读"模板按钮
    await page.click('button:has-text("只读")');
    
    // 等待权限更新
    await page.waitForTimeout(300);
    
    // 验证选中的权限数量
    const checkedCheckboxes = page.locator('.ant-checkbox-wrapper .ant-checkbox-checked');
    const checkedCount = await checkedCheckboxes.count();
    console.log(`只读模板选中的权限数量: ${checkedCount}`);
    
    // 应该选中4个权限
    expect(checkedCount).toBe(4);
    
    // 截图保存
    await page.screenshot({ path: 'test-results/template-readonly.png', fullPage: true });
    
    console.log('✅ 测试通过: 只读模板正确选中4个权限');
    
    // 关闭对话框
    await page.click('button:has-text("取消")');
  });

  test('测试5: 类别全选/全不选功能', async ({ page }) => {
    console.log('开始测试: 类别全选/全不选');
    
    // 点击创建用户按钮
    await page.click('button:has-text("创建用户")');
    
    // 等待创建对话框出现
    await page.waitForSelector('.ant-modal', { timeout: 5000 });
    
    // 等待权限区域渲染
    await page.waitForTimeout(500);
    
    // 找到第一个类别（用户管理）
    const categoryDiv = page.locator('div').filter({ hasText: /^USER/ }).first();
    
    // 点击该类别的"全选"按钮
    const selectAllButton = categoryDiv.locator('button:has-text("全选")').first();
    await selectAllButton.click();
    
    // 等待更新
    await page.waitForTimeout(300);
    
    // 验证该类别的权限是否全部选中
    const categoryCheckboxes = categoryDiv.locator('.ant-checkbox-wrapper');
    const categoryCheckedCount = await categoryDiv.locator('.ant-checkbox-checked').count();
    const categoryTotalCount = await categoryCheckboxes.count();
    
    console.log(`类别总权限数: ${categoryTotalCount}, 选中数: ${categoryCheckedCount}`);
    
    // 该类别的所有权限应该被选中
    expect(categoryCheckedCount).toBe(categoryTotalCount);
    
    // 截图保存
    await page.screenshot({ path: 'test-results/category-select-all.png', fullPage: true });
    
    // 再次点击，测试"全不选"
    const deselectAllButton = categoryDiv.locator('button:has-text("全不选")').first();
    await deselectAllButton.click();
    
    // 等待更新
    await page.waitForTimeout(300);
    
    // 验证该类别的权限是否全部取消选中
    const categoryCheckedCountAfter = await categoryDiv.locator('.ant-checkbox-checked').count();
    console.log(`全不选后，选中数: ${categoryCheckedCountAfter}`);
    
    // 该类别的所有权限应该被取消选中
    expect(categoryCheckedCountAfter).toBe(0);
    
    // 截图保存
    await page.screenshot({ path: 'test-results/category-deselect-all.png', fullPage: true });
    
    console.log('✅ 测试通过: 类别全选/全不选功能正常');
    
    // 关闭对话框
    await page.click('button:has-text("取消")');
  });

  test('测试6: 创建用户完整流程', async ({ page }) => {
    console.log('开始测试: 创建用户完整流程');
    
    const timestamp = Date.now();
    const testUsername = `testuser_${timestamp}`;
    
    // 点击创建用户按钮
    await page.click('button:has-text("创建用户")');
    
    // 等待创建对话框出现
    await page.waitForSelector('.ant-modal', { timeout: 5000 });
    
    // 填写用户信息
    await page.fill('input[placeholder*="用户名"]', testUsername);
    await page.fill('input[type="password"]', 'Test1234');
    await page.fill('input[placeholder*="邮箱"]', `${testUsername}@test.com`);
    await page.fill('input[placeholder*="真实姓名"]', '测试用户');
    
    // 选择操作员模板
    await page.click('button:has-text("操作员")');
    await page.waitForTimeout(300);
    
    // 截图保存
    await page.screenshot({ path: 'test-results/create-user-form.png', fullPage: true });
    
    // 点击确定按钮
    await page.click('.ant-modal button:has-text("确定")');
    
    // 等待创建成功提示
    await page.waitForSelector('.ant-message-success', { timeout: 5000 });
    
    // 等待对话框关闭
    await page.waitForTimeout(1000);
    
    // 验证用户是否出现在列表中
    const userRow = page.locator('tr').filter({ hasText: testUsername });
    await expect(userRow).toBeVisible();
    
    console.log(`✅ 测试通过: 成功创建用户 ${testUsername}`);
    
    // 清理：删除测试用户
    await userRow.locator('button:has-text("删除")').click();
    await page.click('.ant-popconfirm button:has-text("确定")');
    await page.waitForSelector('.ant-message-success', { timeout: 5000 });
    
    console.log(`✅ 清理完成: 已删除测试用户 ${testUsername}`);
  });

  test('测试7: 编辑用户并修改权限', async ({ page }) => {
    console.log('开始测试: 编辑用户并修改权限');
    
    // 先创建一个测试用户
    const timestamp = Date.now();
    const testUsername = `edituser_${timestamp}`;
    
    await page.click('button:has-text("创建用户")');
    await page.waitForSelector('.ant-modal', { timeout: 5000 });
    
    await page.fill('input[placeholder*="用户名"]', testUsername);
    await page.fill('input[type="password"]', 'Test1234');
    await page.fill('input[placeholder*="邮箱"]', `${testUsername}@test.com`);
    await page.fill('input[placeholder*="真实姓名"]', '待编辑用户');
    
    // 选择只读模板（4个权限）
    await page.click('button:has-text("只读")');
    await page.waitForTimeout(300);
    
    await page.click('.ant-modal button:has-text("确定")');
    await page.waitForSelector('.ant-message-success', { timeout: 5000 });
    await page.waitForTimeout(1000);
    
    // 编辑刚创建的用户
    const userRow = page.locator('tr').filter({ hasText: testUsername });
    await userRow.locator('button:has-text("编辑")').click();
    
    // 等待编辑对话框
    await page.waitForSelector('.ant-modal', { timeout: 5000 });
    await page.waitForTimeout(500);
    
    // 验证当前选中4个权限
    let checkedCount = await page.locator('.ant-checkbox-checked').count();
    console.log(`编辑前权限数量: ${checkedCount}`);
    expect(checkedCount).toBe(4);
    
    // 改为操作员模板（11个权限）
    await page.click('button:has-text("操作员")');
    await page.waitForTimeout(300);
    
    // 验证现在选中11个权限
    checkedCount = await page.locator('.ant-checkbox-checked').count();
    console.log(`改为操作员后权限数量: ${checkedCount}`);
    expect(checkedCount).toBe(11);
    
    // 截图保存
    await page.screenshot({ path: 'test-results/edit-user-permissions.png', fullPage: true });
    
    // 保存修改
    await page.click('.ant-modal button:has-text("确定")');
    await page.waitForSelector('.ant-message-success', { timeout: 5000 });
    await page.waitForTimeout(1000);
    
    // 再次编辑，验证权限是否正确保存
    await userRow.locator('button:has-text("编辑")').click();
    await page.waitForSelector('.ant-modal', { timeout: 5000 });
    await page.waitForTimeout(500);
    
    checkedCount = await page.locator('.ant-checkbox-checked').count();
    console.log(`保存后再次打开，权限数量: ${checkedCount}`);
    expect(checkedCount).toBe(11);
    
    console.log('✅ 测试通过: 权限修改并正确保存');
    
    // 关闭对话框
    await page.click('button:has-text("取消")');
    
    // 清理：删除测试用户
    await userRow.locator('button:has-text("删除")').click();
    await page.click('.ant-popconfirm button:has-text("确定")');
    await page.waitForSelector('.ant-message-success', { timeout: 5000 });
    
    console.log(`✅ 清理完成: 已删除测试用户 ${testUsername}`);
  });
});
