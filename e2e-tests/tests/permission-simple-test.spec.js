const { test, expect } = require('@playwright/test');

const BASE_URL = 'http://8.138.114.34';
const ADMIN_USERNAME = 'admin';
const ADMIN_PASSWORD = 'admin123';

test.describe('权限控制简单测试', () => {
  
  test('验证权限控制基本功能', async ({ page }) => {
    console.log('========================================');
    console.log('权限控制简单测试');
    console.log('========================================');
    
    // 1. 管理员登录
    console.log('\n[步骤1] 管理员登录...');
    await page.goto(BASE_URL, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForSelector('input[type="text"]', { timeout: 10000 });
    await page.fill('input[type="text"]', ADMIN_USERNAME);
    await page.fill('input[type="password"]', ADMIN_PASSWORD);
    await page.click('button[type="submit"]');
    await page.waitForURL('**/dashboard', { timeout: 15000 });
    console.log('✓ 管理员登录成功');
    
    // 2. 访问用户管理
    console.log('\n[步骤2] 访问用户管理页面...');
    await page.click('text=用户管理');
    await page.waitForURL('**/users', { timeout: 10000 });
    await page.waitForSelector('table', { timeout: 10000 });
    console.log('✓ 成功访问用户管理页面');
    
    // 3. 验证可以看到用户列表
    const userRows = await page.locator('table tbody tr').count();
    console.log(`✓ 用户列表加载成功，共 ${userRows} 个用户`);
    expect(userRows).toBeGreaterThan(0);
    
    // 4. 验证可以点击创建用户按钮
    console.log('\n[步骤3] 验证创建用户功能...');
    const createButton = page.locator('button:has-text("创建用户")');
    await expect(createButton).toBeVisible();
    console.log('✓ 创建用户按钮可见');
    
    await createButton.click();
    await page.waitForSelector('.ant-modal', { timeout: 5000 });
    console.log('✓ 创建用户对话框打开成功');
    
    // 关闭对话框 - 使用ESC键
    await page.keyboard.press('Escape');
    await page.waitForSelector('.ant-modal', { state: 'hidden', timeout: 5000 });
    await page.waitForTimeout(500);
    
    // 5. 验证可以编辑用户
    console.log('\n[步骤4] 验证编辑用户功能...');
    const adminRow = page.locator('tr').filter({ hasText: 'admin' }).first();
    await adminRow.locator('button:has-text("编辑")').click();
    await page.waitForSelector('.ant-modal', { timeout: 5000 });
    console.log('✓ 编辑用户对话框打开成功');
    
    // 验证权限已加载
    await page.waitForTimeout(1000);
    const checkedCount = await page.locator('.ant-checkbox-checked').count();
    console.log(`✓ admin用户权限已加载: ${checkedCount} 个`);
    expect(checkedCount).toBe(16);
    
    // 关闭对话框 - 使用ESC键
    await page.keyboard.press('Escape');
    await page.waitForSelector('.ant-modal', { state: 'hidden', timeout: 5000 });
    await page.waitForTimeout(500);
    
    console.log('\n========================================');
    console.log('✅ 权限控制基本功能测试通过！');
    console.log('========================================');
    console.log('测试结果:');
    console.log('1. ✅ 管理员可以登录');
    console.log('2. ✅ 管理员可以访问用户管理');
    console.log('3. ✅ 管理员可以查看用户列表');
    console.log('4. ✅ 管理员可以打开创建用户对话框');
    console.log('5. ✅ 管理员可以编辑用户并查看权限');
    console.log('========================================');
  });
});
