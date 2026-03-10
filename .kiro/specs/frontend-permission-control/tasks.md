# 任务列表：前端按钮权限控制

## 1. 准备工作

- [x] 1.1 后端权限注解系统（已完成）
- [x] 1.2 前端权限工具函数（已完成）
- [x] 1.3 登录流程更新（已完成）

## 2. 用户管理页面改造 (Users.jsx)

- [ ] 2.1 导入权限工具函数
  - 在Users.jsx顶部添加：`import { hasPermission } from '../utils/permission'`

- [ ] 2.2 改造"创建用户"按钮
  - 使用`hasPermission('user:create')`包裹创建按钮
  - 确保无权限时按钮不渲染

- [ ] 2.3 改造表格操作列"编辑"按钮
  - 使用`hasPermission('user:edit')`包裹编辑按钮
  - 确保无权限时按钮不渲染

- [ ] 2.4 改造表格操作列"重置密码"按钮
  - 使用`hasPermission('user:edit')`包裹重置密码按钮
  - 确保无权限时按钮不渲染

- [ ] 2.5 改造表格操作列"启用/禁用"按钮
  - 使用`hasPermission('user:edit')`包裹状态切换按钮
  - 确保无权限时按钮不渲染

- [ ] 2.6 改造表格操作列"删除"按钮
  - 使用`hasPermission('user:delete')`包裹删除按钮
  - 确保无权限时按钮不渲染

## 3. 任务管理页面改造 (Tasks.jsx)

- [ ] 3.1 导入权限工具函数
  - 在Tasks.jsx顶部添加：`import { hasPermission } from '../utils/permission'`

- [ ] 3.2 改造"创建任务"按钮
  - 使用`hasPermission('task:create')`包裹创建任务按钮
  - 确保无权限时按钮不渲染

- [ ] 3.3 改造任务表格"查看详情"按钮
  - 使用`hasPermission('task:view')`包裹查看详情按钮
  - 确保无权限时按钮不渲染

- [ ] 3.4 改造任务表格"启动任务"按钮
  - 结合任务状态检查（taskStatus === 'DRAFT'）
  - 使用`hasPermission('task:execute')`包裹启动按钮
  - 确保无权限或状态不符时按钮不渲染

- [ ] 3.5 改造任务表格"停止任务"按钮
  - 结合任务状态检查（taskStatus === 'PENDING' || taskStatus === 'RUNNING'）
  - 使用`hasPermission('task:execute')`包裹停止按钮
  - 确保无权限或状态不符时按钮不渲染

- [ ] 3.6 改造任务表格"重启任务"按钮
  - 结合任务状态检查（taskStatus === 'FAILED' || 'PARTIAL_SUCCESS' || 'STOPPED' || 'CANCELLED'）
  - 使用`hasPermission('task:execute')`包裹重启按钮
  - 确保无权限或状态不符时按钮不渲染

- [ ] 3.7 改造执行实例"查看日志"按钮
  - 使用`hasPermission('task:view')`包裹查看日志按钮
  - 确保无权限时按钮不渲染

- [ ] 3.8 改造执行实例"下载日志"按钮
  - 使用`hasPermission('task:view')`包裹下载日志按钮
  - 确保无权限时按钮不渲染

- [ ] 3.9 改造执行实例"取消执行"按钮
  - 结合执行状态检查（status === 'PENDING' || 'RUNNING' || 'PULLED'）
  - 使用`hasPermission('task:execute')`包裹取消按钮
  - 确保无权限或状态不符时按钮不渲染

## 4. Agent分组页面改造 (AgentGroups.jsx)

- [ ] 4.1 导入权限工具函数
  - 在AgentGroups.jsx顶部添加：`import { hasPermission } from '../utils/permission'`

- [ ] 4.2 改造"创建分组"按钮
  - 使用`hasPermission('agent:group')`包裹创建分组按钮
  - 确保无权限时按钮不渲染

- [ ] 4.3 改造表格操作列"查看"按钮
  - 使用`hasPermission('agent:group')`包裹查看按钮
  - 确保无权限时按钮不渲染

- [ ] 4.4 改造表格操作列"编辑"按钮
  - 使用`hasPermission('agent:group')`包裹编辑按钮
  - 确保无权限时按钮不渲染

- [ ] 4.5 改造表格操作列"删除"按钮
  - 使用`hasPermission('agent:group')`包裹删除按钮
  - 确保无权限时按钮不渲染

- [ ] 4.6 改造详情抽屉"添加成员"功能
  - 使用`hasPermission('agent:group')`包裹添加成员的Select组件
  - 确保无权限时功能不显示

- [ ] 4.7 改造详情抽屉"移除成员"按钮
  - 使用`hasPermission('agent:group')`包裹移除按钮
  - 确保无权限时按钮不渲染

## 5. 侧边栏菜单改造 (Sidebar.jsx)

- [ ] 5.1 导入权限工具函数
  - 在Sidebar.jsx顶部添加：`import { hasPermission } from '../../utils/permission'`

- [ ] 5.2 定义基础菜单项数组
  - 创建baseMenuItems数组，包含无需权限的菜单项
  - 包括：仪表盘、客户端管理、任务管理、脚本管理

- [ ] 5.3 定义权限菜单项数组
  - 创建permissionMenuItems数组，包含需要权限的菜单项
  - 每个菜单项包含permission属性
  - 包括：Agent分组（agent:group）、用户管理（user:view）

- [ ] 5.4 实现菜单项过滤逻辑
  - 使用filter()方法根据权限过滤permissionMenuItems
  - 使用hasPermission()检查每个菜单项的权限
  - 合并baseMenuItems和过滤后的permissionMenuItems

- [ ] 5.5 更新Menu组件
  - 将过滤后的菜单项传递给Menu组件的items属性
  - 确保菜单顺序和样式保持不变

## 6. 单元测试

- [ ] 6.1 测试hasPermission()方法
  - 测试用户拥有权限时返回true
  - 测试用户无权限时返回false
  - 测试空权限参数时返回true
  - 测试用户未登录时返回false

- [ ] 6.2 测试getUserPermissions()方法
  - 测试正常情况返回权限数组
  - 测试localStorage为空时返回空数组
  - 测试数据格式错误时返回空数组

- [ ] 6.3 测试hasAnyPermission()方法
  - 测试拥有其中一个权限时返回true
  - 测试不拥有任何权限时返回false

- [ ] 6.4 测试hasAllPermissions()方法
  - 测试拥有全部权限时返回true
  - 测试缺少一个权限时返回false

## 7. 集成测试

- [ ] 7.1 测试Users页面权限控制
  - 使用管理员账号登录，验证所有按钮可见
  - 使用只读账号登录，验证只有查看功能可见
  - 使用无权限账号登录，验证所有操作按钮不可见

- [ ] 7.2 测试Tasks页面权限控制
  - 使用操作员账号登录，验证创建和执行按钮可见
  - 使用只读账号登录，验证只有查看功能可见
  - 验证任务状态与权限的组合逻辑

- [ ] 7.3 测试AgentGroups页面权限控制
  - 使用有agent:group权限的账号登录，验证所有功能可见
  - 使用无agent:group权限的账号登录，验证所有功能不可见

- [ ] 7.4 测试Sidebar菜单权限控制
  - 使用不同权限组合的账号登录
  - 验证菜单项正确过滤
  - 验证基础菜单项始终显示

## 8. 端到端测试

- [ ] 8.1 测试完整的用户管理流程
  - 管理员创建新用户并分配权限
  - 使用新用户登录
  - 验证权限生效

- [ ] 8.2 测试权限变更流程
  - 用户A登录
  - 管理员修改用户A的权限
  - 用户A刷新页面
  - 验证新权限生效

- [ ] 8.3 测试无权限操作拦截
  - 只读用户登录
  - 验证前端隐藏操作按钮
  - 尝试通过API直接调用（如果可能）
  - 验证后端返回403

- [ ] 8.4 测试错误处理
  - 测试localStorage不可访问的情况
  - 测试权限数据格式错误的情况
  - 验证系统显示友好的错误提示

## 9. 性能优化（可选）

- [ ] 9.1 实现权限Context
  - 创建PermissionContext.jsx
  - 使用useMemo缓存权限数据
  - 提供usePermissions和useHasPermission hooks

- [ ] 9.2 优化条件渲染
  - 使用短路运算符替代三元表达式
  - 避免嵌套条件渲染
  - 使用React.memo优化组件

- [ ] 9.3 优化菜单过滤
  - 使用useMemo缓存过滤结果
  - 只在权限变化时重新计算

## 10. 文档更新

- [ ] 10.1 更新开发文档
  - 记录权限控制的使用方法
  - 添加代码示例
  - 说明权限代码与后端的对应关系

- [ ] 10.2 更新用户手册
  - 说明不同角色的权限范围
  - 解释为什么某些按钮不可见

- [ ] 10.3 创建测试报告
  - 记录测试结果
  - 列出已知问题和限制
  - 提供验收标准检查清单
