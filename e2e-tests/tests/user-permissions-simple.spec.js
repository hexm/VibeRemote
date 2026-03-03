const { test, expect } = require('@playwright/test');

// 测试配置
const BASE_URL = 'http://8.138.114.34';
const ADMIN_USERNAME = 'admin';
const ADMIN_PASSWORD = 'admin123';

test.describe('用户权限功能简化测试', () => {
  
  test('核心测试: 编辑admin用户验证权限加载', async ({ page }) => {
    // 监听浏览器控制台日志
    page.on('console', msg => {
      if (msg.text().includes('[DEBUG]')) {
        console.log('浏览器控制台:', msg.text());
      }
    });
    
    console.log('========================================');
    console.log('开始测试: 编辑admin用户权限加载');
    console.log('========================================');
    
    // 1. 访问登录页面
    console.log('\n[步骤1] 访问登录页面...');
    await page.goto(BASE_URL, { waitUntil: 'domcontentloaded', timeout: 30000 });
    console.log('✓ 页面加载完成');
    
    // 等待登录表单出现
    await page.waitForSelector('input[type="text"]', { timeout: 10000 });
    console.log('✓ 登录表单已显示');
    
    // 2. 登录
    console.log('\n[步骤2] 登录系统...');
    await page.fill('input[type="text"]', ADMIN_USERNAME);
    await page.fill('input[type="password"]', ADMIN_PASSWORD);
    await page.click('button[type="submit"]');
    console.log('✓ 提交登录表单');
    
    // 等待登录成功，跳转到仪表盘
    await page.waitForURL('**/dashboard', { timeout: 15000 });
    console.log('✓ 登录成功，已跳转到仪表盘');
    
    // 3. 导航到用户管理页面
    console.log('\n[步骤3] 导航到用户管理页面...');
    await page.click('text=用户管理');
    await page.waitForURL('**/users', { timeout: 10000 });
    console.log('✓ 已进入用户管理页面');
    
    // 等待用户列表加载
    await page.waitForSelector('table', { timeout: 10000 });
    console.log('✓ 用户列表已加载');
    
    // 4. 点击编辑admin用户
    console.log('\n[步骤4] 点击编辑admin用户...');
    const adminRow = page.locator('tr').filter({ hasText: 'admin' }).first();
    await adminRow.locator('button:has-text("编辑")').click();
    console.log('✓ 点击编辑按钮');
    
    // 等待编辑对话框出现
    await page.waitForSelector('.ant-modal', { timeout: 10000 });
    console.log('✓ 编辑对话框已显示');
    
    // 验证对话框标题
    const modalTitle = await page.locator('.ant-modal-title').textContent();
    console.log(`✓ 对话框标题: ${modalTitle}`);
    expect(modalTitle).toContain('编辑用户');
    
    // 5. 等待权限复选框渲染（关键步骤）
    console.log('\n[步骤5] 等待权限复选框渲染...');
    await page.waitForTimeout(1000); // 等待1秒确保useEffect执行完成
    
    // 6. 验证权限复选框
    console.log('\n[步骤6] 验证权限复选框...');
    const checkboxes = page.locator('.ant-checkbox-wrapper');
    const checkboxCount = await checkboxes.count();
    console.log(`找到 ${checkboxCount} 个权限复选框`);
    
    // 验证至少有16个权限复选框
    expect(checkboxCount).toBeGreaterThanOrEqual(16);
    console.log('✓ 权限复选框数量正确');
    
    // 7. 验证选中的权限数量（核心验证）
    console.log('\n[步骤7] 验证选中的权限数量...');
    const checkedCheckboxes = page.locator('.ant-checkbox-wrapper .ant-checkbox-checked');
    const checkedCount = await checkedCheckboxes.count();
    console.log(`选中的权限数量: ${checkedCount}`);
    
    // 截图保存
    await page.screenshot({ 
      path: 'test-results/edit-admin-permissions-final.png', 
      fullPage: true 
    });
    console.log('✓ 截图已保存: test-results/edit-admin-permissions-final.png');
    
    // admin应该有16个权限全部选中
    if (checkedCount === 16) {
      console.log('\n========================================');
      console.log('✅ 测试通过！admin用户的16个权限全部正确显示为选中状态');
      console.log('========================================');
    } else {
      console.log('\n========================================');
      console.log(`❌ 测试失败！期望16个权限选中，实际${checkedCount}个`);
      console.log('========================================');
    }
    
    expect(checkedCount).toBe(16);
    
    // 8. 测试快捷模板（额外验证）
    console.log('\n[步骤8] 测试快捷模板功能...');
    
    // 点击操作员模板
    await page.click('button:has-text("操作员")');
    await page.waitForTimeout(500);
    
    const operatorChecked = await page.locator('.ant-checkbox-checked').count();
    console.log(`操作员模板选中: ${operatorChecked} 个权限`);
    expect(operatorChecked).toBe(11);
    console.log('✓ 操作员模板正常');
    
    // 点击管理员模板
    await page.click('button:has-text("管理员")');
    await page.waitForTimeout(500);
    
    const adminChecked = await page.locator('.ant-checkbox-checked').count();
    console.log(`管理员模板选中: ${adminChecked} 个权限`);
    expect(adminChecked).toBe(16);
    console.log('✓ 管理员模板正常');
    
    // 点击只读模板
    await page.click('button:has-text("只读")');
    await page.waitForTimeout(500);
    
    const readonlyChecked = await page.locator('.ant-checkbox-checked').count();
    console.log(`只读模板选中: ${readonlyChecked} 个权限`);
    expect(readonlyChecked).toBe(4);
    console.log('✓ 只读模板正常');
    
    console.log('\n========================================');
    console.log('✅ 所有测试通过！');
    console.log('========================================');
  });
});
