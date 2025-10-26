// Axios instance with base configuration
const api = axios.create({
    baseURL: 'http://localhost:8080/api',
    timeout: 10000,
});

// Interceptor to add JWT token to requests
api.interceptors.request.use(config => {
    const token = localStorage.getItem('jwt_token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
}, error => {
    return Promise.reject(error);
});

// Interceptor to handle 401 Unauthorized errors
api.interceptors.response.use(response => response, error => {
    if (error.response && error.response.status === 401) {
        // Token is invalid or expired, redirect to login
        localStorage.removeItem('jwt_token');
        if (window.location.pathname !== '/login.html') {
            window.location.href = '/login.html';
        }
    }
    return Promise.reject(error);
});

// Helper function to format date and time
function formatDateTime(isoString) {
    if (!isoString) return '-';
    return new Date(isoString).toLocaleString();
}

// 导航栏工具函数
function initNavbar() {
    // 检查登录状态并显示用户信息
    const token = localStorage.getItem('jwt_token');
    const userNameSpan = document.getElementById('navbar-username');
    const logoutBtn = document.getElementById('navbar-logout');
    
    if (token && userNameSpan) {
        try {
            const payload = JSON.parse(atob(token.split('.')[1]));
            userNameSpan.textContent = payload.sub || 'dev-user';
            userNameSpan.style.display = 'inline';
            if (logoutBtn) logoutBtn.style.display = 'inline-block';
        } catch (e) {
            console.warn('Invalid JWT token');
            userNameSpan.textContent = 'dev-user';
            userNameSpan.style.display = 'inline';
            if (logoutBtn) logoutBtn.style.display = 'inline-block';
        }
    } else if (userNameSpan) {
        userNameSpan.textContent = 'dev-user';
        userNameSpan.style.display = 'inline';
        if (logoutBtn) logoutBtn.style.display = 'inline-block';
    }
}

// 退出登录
function handleLogout() {
    localStorage.removeItem('jwt_token');
    window.location.href = '/login.html';
}

// 页面过渡效果
function hidePageLoader() {
    const loader = document.getElementById('page-loader');
    if (loader) {
        loader.classList.remove('show');
    }
}

// 页面加载时初始化导航栏
document.addEventListener('DOMContentLoaded', () => {
    initNavbar();
    
    // 绑定退出登录按钮
    const logoutBtn = document.getElementById('navbar-logout');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', handleLogout);
    }
    
    // 页面加载完成，隐藏加载遮罩（如果有的话）
    setTimeout(hidePageLoader, 100);
});
