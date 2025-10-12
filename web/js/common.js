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
