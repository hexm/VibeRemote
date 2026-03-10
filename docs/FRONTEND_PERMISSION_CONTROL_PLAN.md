# 前端按钮权限控制实施计划

## 目标
根据用户权限动态显示/隐藏操作按钮，提升用户体验，避免用户点击无权限按钮后才收到403错误。

## 实施步骤

### 1. 创建权限工具函数 ✅

创建 `web-modern/src/utils/permission.js`：

```javascript
/**
 * 从localStorage获取当前用户权限列表
 */
export const getUserPermissions = () => {
  const userStr = localStorage.getItem('user');
  if (!userStr) return [];
  
  try {
    const user = JSON.parse(userStr);
    return user.permissions || [];
  } catch (e) {
    return [];
  }
};

/**
 * 检查用户是否拥有指定权限
 * @param {string} permission - 权限代码，如 'user:create'
 * @returns {boolean}
 */
export const hasPermission = (permission) => {
  const permissions = getUserPermissions();
  return permissions.includes(permission);
};

/**
 * 检查用户是否拥有任意一个权限
 * @param {string[]} permissions - 权限代码数组
 * @returns {boolean}
 */
export const hasAnyPermission = (permissions) => {
  const userPermissions = getUserPermissions();
  return permissions.some(p => userPermissions.includes(p));
};

/**
 * 检查用户是否拥有所有权限
 * @param {string[]} permissions - 权限代码数组
 * @returns {boolean}
 */
export const hasAllPermissions = (permissions) => {
  const userPermissions = getUserPermissions();
  return permissions.every(p => userPermissions.includes(p));
};
```

### 2. 修改登录逻辑，存储用户权限

修改 `web-modern/src/pages/Login.jsx` 和 `web-modern/src/pages/SimpleLogin.jsx`：

登录成功后，获取用户完整信息（包括权限列表）并存储到localStorage。

### 3. 为各页面添加权限控制

#### 3.1 用户管理页面 (Users.jsx)

需要控制的按钮：
- **创建用户按钮** - 需要 `user:create` 权限
- **编辑按钮** - 需要 `user:edit` 权限
- **删除按钮** - 需要 `user:delete` 权限
- **编辑权限按钮** - 需要 `user:edit` 权限
- **启用/禁用按钮** - 需要 `user:edit` 权限

#### 3.2 Agent分组页面 (AgentGroups.jsx)

需要控制的按钮：
- **创建分组按钮** - 需要 `agent:group` 权限
- **编辑按钮** - 需要 `agent:group` 权限
- **删除按钮** - 需要 `agent:group` 权限
- **添加Agent按钮** - 需要 `agent:group` 权限
- **移除Agent按钮** - 需要 `agent:group` 权限

#### 3.3 任务管理页面 (Tasks.jsx)

需要控制的按钮：
- **创建任务按钮** - 需要 `task:create` 权限
- **启动按钮** - 需要 `task:execute` 权限
- **停止按钮** - 需要 `task:execute` 权限
- **重启按钮** - 需要 `task:execute` 权限
- **取消按钮** - 需要 `task:execute` 权限
- **查看日志按钮** - 需要 `log:view` 权限
- **下载日志按钮** - 需要 `log:view` 权限

#### 3.4 Agent管理页面 (Agents.jsx)

需要控制的按钮：
- **查看任务按钮** - 需要 `agent:view` 权限（已有查看权限才能进入页面）
- **查看分组按钮** - 需要 `agent:view` 权限

#### 3.5 脚本管理页面 (Scripts.jsx)

需要控制的按钮：
- **创建脚本按钮** - 需要 `script:create` 权限
- **编辑按钮** - 需要 `script:edit` 权限
- **删除按钮** - 需要 `script:delete` 权限
- **执行按钮** - 需要 `task:create` 权限（执行脚本实际是创建任务）

### 4. 侧边栏菜单权限控制

修改 `web-modern/src/components/Layout/Sidebar.jsx`：

根据用户权限动态显示菜单项：
- **仪表盘** - 所有登录用户可见
- **Agent管理** - 需要 `agent:view` 权限
- **任务管理** - 需要 `task:view` 权限
- **脚本管理** - 需要 `script:view` 权限
- **用户管理** - 需要 `user:view` 权限
- **Agent分组** - 需要 `agent:group` 权限

### 5. 创建权限控制组件（可选）

创建 `web-modern/src/components/PermissionButton.jsx`：

```javascript
import { Button } from 'antd';
import { hasPermission } from '../utils/permission';

/**
 * 带权限控制的按钮组件
 */
const PermissionButton = ({ permission, children, ...props }) => {
  if (!hasPermission(permission)) {
    return null; // 无权限时不显示按钮
  }
  
  return <Button {...props}>{children}</Button>;
};

export default PermissionButton;
```

使用示例：
```javascript
<PermissionButton 
  permission="user:create" 
  type="primary" 
  onClick={handleCreate}
>
  创建用户
</PermissionButton>
```

## 实施优先级

### 第一优先级（立即实施）
1. ✅ 创建权限工具函数
2. ✅ 修改登录逻辑，存储权限
3. ✅ 用户管理页面权限控制
4. ✅ 任务管理页面权限控制

### 第二优先级（本周完成）
1. Agent分组页面权限控制
2. 侧边栏菜单权限控制
3. Agent管理页面权限控制

### 第三优先级（下周完成）
1. 脚本管理页面权限控制（功能未完全实现）
2. 创建通用权限控制组件
3. 完善权限提示信息

## 用户体验优化

### 1. 无权限时的提示
- 按钮隐藏：直接不显示无权限的按钮
- 菜单置灰：可选择将无权限菜单置灰而不是隐藏
- Tooltip提示：鼠标悬停时显示"需要XX权限"

### 2. 权限变更后的处理
- 用户权限被修改后，下次登录生效
- 可选：实现实时权限刷新（通过WebSocket或轮询）

### 3. 降级处理
- 如果无法获取权限列表，默认隐藏所有操作按钮
- 保留查看功能，确保基本可用性

## 测试场景

### 场景1：管理员用户
- 登录admin账号
- 所有按钮都应该可见
- 所有菜单都应该可见

### 场景2：只读用户
- 创建只有view权限的用户
- 登录该用户
- 所有创建、编辑、删除按钮应该隐藏
- 只能看到查看相关的功能

### 场景3：操作员用户
- 创建有execute权限的用户
- 登录该用户
- 可以看到启动、停止、重启按钮
- 不能看到创建、删除按钮

### 场景4：无权限用户
- 创建没有任何权限的用户
- 登录该用户
- 应该只能看到仪表盘
- 其他菜单应该隐藏或置灰

## 技术细节

### 权限存储位置
- localStorage: 存储用户信息和权限列表
- 登录时获取并存储
- 退出登录时清除

### 权限检查时机
- 组件渲染时：使用条件渲染
- 按钮点击前：双重检查（前端+后端）
- 路由跳转时：可选的路由守卫

### 性能优化
- 权限列表缓存在内存中
- 避免频繁读取localStorage
- 使用React.memo优化权限组件

## 安全注意事项

1. **前端权限控制不是安全措施**
   - 前端控制只是用户体验优化
   - 真正的安全控制在后端
   - 前端隐藏按钮不代表API不可访问

2. **不要在前端暴露敏感信息**
   - 权限列表可以存储在前端
   - 但不要存储敏感的业务数据

3. **保持前后端权限一致**
   - 前端权限检查应该与后端注解一致
   - 定期审查权限配置

## 文件清单

### 新增文件
1. `web-modern/src/utils/permission.js` - 权限工具函数
2. `web-modern/src/components/PermissionButton.jsx` - 权限按钮组件（可选）

### 修改文件
1. `web-modern/src/pages/Login.jsx` - 存储用户权限
2. `web-modern/src/pages/SimpleLogin.jsx` - 存储用户权限
3. `web-modern/src/pages/Users.jsx` - 添加按钮权限控制
4. `web-modern/src/pages/Tasks.jsx` - 添加按钮权限控制
5. `web-modern/src/pages/AgentGroups.jsx` - 添加按钮权限控制
6. `web-modern/src/pages/Agents.jsx` - 添加按钮权限控制
7. `web-modern/src/components/Layout/Sidebar.jsx` - 添加菜单权限控制

## 预期效果

实施完成后：
1. 用户只能看到自己有权限的按钮和菜单
2. 减少403错误的发生
3. 提升用户体验
4. 界面更加简洁，不会显示无用的按钮

## 时间估算

- 权限工具函数：30分钟
- 登录逻辑修改：30分钟
- 用户管理页面：1小时
- 任务管理页面：1小时
- Agent分组页面：1小时
- 侧边栏菜单：30分钟
- 测试和调试：2小时

**总计：约6-7小时**

---

**创建时间**: 2026-03-03  
**状态**: 待实施  
**优先级**: 高
