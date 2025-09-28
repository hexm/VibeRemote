// LightScript 前端配置文件

const CONFIG = {
    // API 基础地址
    API_BASE_URL: 'http://localhost:8080',
    
    // API 端点
    API_ENDPOINTS: {
        // 认证相关
        LOGIN: '/api/auth/login',
        REGISTER: '/api/auth/register',
        CHANGE_PASSWORD: '/api/auth/change-password',
        
        // Web 管理相关
        DASHBOARD_STATS: '/api/web/dashboard/stats',
        AGENTS: '/api/web/agents',
        AGENT_TASKS: '/api/web/agents/{agentId}/tasks',
        TASKS: '/api/web/tasks',
        TASK_DETAIL: '/api/web/tasks/{taskId}',
        TASK_LOGS: '/api/web/tasks/{taskId}/logs',
        CREATE_TASK: '/api/web/tasks/create',
        BATCH_TASKS: '/api/web/tasks/batch',
        
        // Agent 相关 (调试用)
        AGENT_DEBUG_ENQUEUE: '/api/agent/debug/enqueue'
    },
    
    // 应用设置
    APP_SETTINGS: {
        // 页面标题
        TITLE: 'LightScript 管理平台',
        
        // 分页设置
        PAGE_SIZE: 20,
        
        // 轮询间隔 (毫秒)
        POLLING_INTERVAL: 30000,
        
        // 心跳检测间隔 (毫秒)
        HEARTBEAT_INTERVAL: 60000,
        
        // 请求超时时间 (毫秒)
        REQUEST_TIMEOUT: 10000
    },
    
    // 本地存储键名
    STORAGE_KEYS: {
        TOKEN: 'lightscript_token',
        USER_INFO: 'lightscript_user_info',
        REMEMBER_LOGIN: 'lightscript_remember_login'
    },
    
    // 任务状态映射
    TASK_STATUS: {
        PENDING: { text: '等待中', type: 'info' },
        RUNNING: { text: '运行中', type: 'warning' },
        SUCCESS: { text: '成功', type: 'success' },
        FAILED: { text: '失败', type: 'danger' },
        TIMEOUT: { text: '超时', type: 'danger' },
        CANCELLED: { text: '已取消', type: 'info' }
    },
    
    // 脚本语言选项
    SCRIPT_LANGUAGES: [
        { label: 'Bash', value: 'bash' },
        { label: 'PowerShell', value: 'powershell' },
        { label: 'CMD', value: 'cmd' }
    ],
    
    // 操作系统类型
    OS_TYPES: {
        WINDOWS: 'Windows',
        LINUX: 'Linux'
    },
    
    // 客户端状态
    AGENT_STATUS: {
        ONLINE: { text: '在线', type: 'success' },
        OFFLINE: { text: '离线', type: 'danger' }
    }
};

// 工具函数
const Utils = {
    // 格式化 API URL
    formatApiUrl(endpoint, params = {}) {
        let url = CONFIG.API_BASE_URL + endpoint;
        Object.keys(params).forEach(key => {
            url = url.replace(`{${key}}`, params[key]);
        });
        return url;
    },
    
    // 格式化日期时间
    formatDateTime(dateTime) {
        if (!dateTime) return '-';
        const date = new Date(dateTime);
        return date.toLocaleString('zh-CN');
    },
    
    // 格式化文件大小
    formatFileSize(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    },
    
    // 生成随机ID
    generateId() {
        return Math.random().toString(36).substr(2, 9);
    },
    
    // 深拷贝对象
    deepClone(obj) {
        return JSON.parse(JSON.stringify(obj));
    },
    
    // 防抖函数
    debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    },
    
    // 节流函数
    throttle(func, limit) {
        let inThrottle;
        return function() {
            const args = arguments;
            const context = this;
            if (!inThrottle) {
                func.apply(context, args);
                inThrottle = true;
                setTimeout(() => inThrottle = false, limit);
            }
        }
    }
};

// 导出配置 (如果在模块环境中)
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { CONFIG, Utils };
}
