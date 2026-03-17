/**
 * 权限工具函数
 * 用于前端权限检查和按钮显示控制
 */

/**
 * 从localStorage获取当前用户权限列表
 * @returns {string[]} 权限代码数组
 */
export const getUserPermissions = () => {
  const userStr = localStorage.getItem('user');
  if (!userStr) return [];
  
  try {
    const user = JSON.parse(userStr);
    return user.permissions || [];
  } catch (e) {
    console.error('Failed to parse user permissions:', e);
    return [];
  }
};

/**
 * 检查用户是否拥有指定权限
 * @param {string} permission - 权限代码，如 'user:create'
 * @returns {boolean}
 */
export const hasPermission = (permission) => {
  if (!permission) return true; // 如果没有指定权限要求，默认允许
  const permissions = getUserPermissions();
  return permissions.includes(permission);
};

/**
 * 检查用户是否拥有任意一个权限
 * @param {string[]} permissions - 权限代码数组
 * @returns {boolean}
 */
export const hasAnyPermission = (permissions) => {
  if (!permissions || permissions.length === 0) return true;
  const userPermissions = getUserPermissions();
  return permissions.some(p => userPermissions.includes(p));
};

/**
 * 检查用户是否拥有所有权限
 * @param {string[]} permissions - 权限代码数组
 * @returns {boolean}
 */
export const hasAllPermissions = (permissions) => {
  if (!permissions || permissions.length === 0) return true;
  const userPermissions = getUserPermissions();
  return permissions.every(p => userPermissions.includes(p));
};

/**
 * 获取当前用户信息
 * @returns {object|null}
 */
export const getCurrentUser = () => {
  const userStr = localStorage.getItem('user');
  if (!userStr) return null;
  
  try {
    return JSON.parse(userStr);
  } catch (e) {
    console.error('Failed to parse user info:', e);
    return null;
  }
};

/**
 * 检查用户是否已登录
 * @returns {boolean}
 */
export const isLoggedIn = () => {
  return !!localStorage.getItem('token');
};
