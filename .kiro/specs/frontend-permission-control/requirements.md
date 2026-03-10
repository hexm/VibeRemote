# 需求文档：前端按钮权限控制

## 简介

本需求文档描述前端按钮权限控制功能的完整需求。该功能基于已完成的后端权限注解和前端权限工具函数，实现根据用户权限动态显示/隐藏操作按钮，提升用户体验，避免用户点击无权限按钮后才收到403错误。

## 术语表

- **System**: 前端权限控制系统
- **Permission_Util**: 权限工具函数模块（permission.js）
- **User**: 登录用户
- **Button**: 页面上的操作按钮
- **Menu_Item**: 侧边栏菜单项
- **Permission_Code**: 权限代码字符串（如'user:create'）
- **LocalStorage**: 浏览器本地存储
- **Backend_API**: 后端API接口

## 需求

### 需求 1: 权限数据获取与存储

**用户故事**: 作为系统用户，我希望登录后系统能够获取并存储我的权限信息，以便前端能够根据权限控制界面元素。

#### 验收标准

1. WHEN 用户成功登录 THEN THE System SHALL 从后端API获取用户权限列表
2. WHEN 权限列表获取成功 THEN THE System SHALL 将权限数据存储到LocalStorage中
3. WHEN 用户注销 THEN THE System SHALL 清除LocalStorage中的权限数据
4. THE Permission_Util SHALL 提供getUserPermissions()方法从LocalStorage读取权限列表
5. IF LocalStorage不可访问或数据格式错误 THEN THE Permission_Util SHALL 返回空数组

### 需求 2: 单个权限检查

**用户故事**: 作为开发者，我需要检查用户是否拥有特定权限，以便控制按钮的显示。

#### 验收标准

1. THE Permission_Util SHALL 提供hasPermission(permission)方法检查单个权限
2. WHEN 用户拥有指定权限 THEN THE hasPermission方法 SHALL 返回true
3. WHEN 用户不拥有指定权限 THEN THE hasPermission方法 SHALL 返回false
4. WHEN permission参数为null或空字符串 THEN THE hasPermission方法 SHALL 返回true
5. WHEN 用户未登录 THEN THE hasPermission方法 SHALL 返回false

### 需求 3: 多权限检查

**用户故事**: 作为开发者，我需要检查用户是否拥有多个权限中的任意一个或全部，以便实现复杂的权限控制逻辑。

#### 验收标准

1. THE Permission_Util SHALL 提供hasAnyPermission(permissions)方法检查是否拥有任意一个权限
2. WHEN 用户拥有权限列表中的任意一个 THEN THE hasAnyPermission方法 SHALL 返回true
3. THE Permission_Util SHALL 提供hasAllPermissions(permissions)方法检查是否拥有所有权限
4. WHEN 用户拥有权限列表中的所有权限 THEN THE hasAllPermissions方法 SHALL 返回true
5. WHEN 用户缺少任意一个权限 THEN THE hasAllPermissions方法 SHALL 返回false

### 需求 4: 用户管理页面按钮权限控制

**用户故事**: 作为用户管理员，我希望只看到我有权限操作的按钮，以便清楚了解我能执行的操作。

#### 验收标准

1. WHEN 用户拥有user:create权限 THEN THE System SHALL 显示"创建用户"按钮
2. WHEN 用户拥有user:edit权限 THEN THE System SHALL 显示"编辑"、"重置密码"和"启用/禁用"按钮
3. WHEN 用户拥有user:delete权限 THEN THE System SHALL 显示"删除"按钮
4. WHEN 用户不拥有相应权限 THEN THE System SHALL 隐藏对应按钮（不渲染）
5. WHEN 用户点击有权限的按钮 THEN THE System SHALL 执行相应操作

### 需求 5: 任务管理页面按钮权限控制

**用户故事**: 作为任务操作员，我希望只看到我有权限操作的任务按钮，以便高效地管理任务。

#### 验收标准

1. WHEN 用户拥有task:create权限 THEN THE System SHALL 显示"创建任务"按钮
2. WHEN 用户拥有task:view权限 THEN THE System SHALL 显示"查看详情"和"查看日志"按钮
3. WHEN 用户拥有task:execute权限 THEN THE System SHALL 显示"启动"、"停止"、"重启"和"取消执行"按钮
4. WHEN 任务状态为DRAFT且用户拥有task:execute权限 THEN THE System SHALL 显示"启动任务"按钮
5. WHEN 任务状态为RUNNING且用户拥有task:execute权限 THEN THE System SHALL 显示"停止任务"按钮
6. WHEN 任务状态为FAILED、PARTIAL_SUCCESS、STOPPED或CANCELLED且用户拥有task:execute权限 THEN THE System SHALL 显示"重启任务"按钮

### 需求 6: Agent分组页面按钮权限控制

**用户故事**: 作为Agent管理员，我希望只看到我有权限操作的分组管理按钮，以便管理Agent分组。

#### 验收标准

1. WHEN 用户拥有agent:group权限 THEN THE System SHALL 显示"创建分组"按钮
2. WHEN 用户拥有agent:group权限 THEN THE System SHALL 显示"查看"、"编辑"和"删除"按钮
3. WHEN 用户拥有agent:group权限 THEN THE System SHALL 显示"添加成员"和"移除成员"功能
4. WHEN 用户不拥有agent:group权限 THEN THE System SHALL 隐藏所有分组管理按钮

### 需求 7: 侧边栏菜单权限控制

**用户故事**: 作为系统用户，我希望侧边栏只显示我有权限访问的菜单项，以便快速找到我能使用的功能。

#### 验收标准

1. THE System SHALL 始终显示基础菜单项（仪表盘、客户端管理、任务管理、脚本管理）
2. WHEN 用户拥有user:view权限 THEN THE System SHALL 显示"用户管理"菜单项
3. WHEN 用户拥有agent:group权限 THEN THE System SHALL 显示"Agent分组"菜单项
4. WHEN 用户不拥有相应权限 THEN THE System SHALL 隐藏对应菜单项
5. THE System SHALL 根据权限动态过滤菜单项列表

### 需求 8: 权限检查与后端一致性

**用户故事**: 作为系统架构师，我需要确保前端权限检查与后端权限验证保持一致，以便提供准确的用户体验。

#### 验收标准

1. THE System SHALL 使用与后端相同的权限代码进行检查
2. WHEN 前端显示按钮 THEN THE Backend_API SHALL 允许执行对应操作
3. WHEN 前端隐藏按钮 THEN THE Backend_API SHALL 拒绝执行对应操作（返回403）
4. THE System SHALL 在登录时同步后端返回的权限列表
5. WHEN 后端权限验证失败 THEN THE System SHALL 显示友好的错误提示

### 需求 9: 错误处理与降级

**用户故事**: 作为系统用户，当权限数据出现问题时，我希望系统能够安全地处理错误，而不是崩溃。

#### 验收标准

1. IF LocalStorage不可访问 THEN THE Permission_Util SHALL 返回空数组并记录错误
2. IF 权限数据格式错误 THEN THE Permission_Util SHALL 返回空数组并记录错误
3. IF JSON解析失败 THEN THE Permission_Util SHALL 捕获异常并返回空数组
4. WHEN 权限检查失败 THEN THE System SHALL 默认隐藏受保护的按钮
5. WHEN API请求返回403 THEN THE System SHALL 显示"权限不足"错误提示

### 需求 10: 性能要求

**用户故事**: 作为系统用户，我希望权限检查不会影响页面加载和响应速度。

#### 验收标准

1. THE hasPermission方法 SHALL 在1毫秒内完成单次检查
2. THE System SHALL 避免在每次渲染时重复读取LocalStorage
3. THE System SHALL 使用缓存机制减少权限数据解析次数
4. WHEN 页面包含多个权限检查 THEN THE 页面渲染时间增加 SHALL 小于50毫秒
5. THE System SHALL 使用短路运算符优化条件渲染

### 需求 11: 安全要求

**用户故事**: 作为安全管理员，我需要确保前端权限控制不会成为安全漏洞，后端必须进行最终验证。

#### 验收标准

1. THE System SHALL 仅将前端权限检查用于改善用户体验，不作为安全保障
2. THE Backend_API SHALL 始终验证用户权限，不依赖前端检查
3. THE System SHALL 不在LocalStorage中存储敏感数据
4. WHEN 用户注销 THEN THE System SHALL 清除所有权限相关数据
5. THE System SHALL 实施双重保护（前端隐藏+后端验证）

### 需求 12: 条件渲染实现

**用户故事**: 作为开发者，我需要使用条件渲染来控制按钮显示，以便保持代码简洁。

#### 验收标准

1. THE System SHALL 使用条件渲染隐藏无权限按钮（而非禁用）
2. WHEN 用户无权限 THEN THE Button SHALL 不渲染到DOM中
3. THE System SHALL 使用短路运算符（&&）实现条件渲染
4. THE System SHALL 避免使用三元表达式返回null
5. THE System SHALL 保持现有事件处理逻辑不变
