import axios from 'axios'

const API_BASE_URL = 'http://localhost:8080/api'

// 创建axios实例
const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
})

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error) => {
    console.error('API Error:', error)
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      // 不要直接跳转，让React处理状态变化
      return Promise.reject(error.response?.data || error)
    }
    return Promise.reject(error.response?.data || error)
  }
)

export const authService = {
  // 登录
  async login(credentials) {
    try {
      const response = await api.post('/auth/login', credentials)
      return {
        token: response.token,
        user: {
          username: response.username,
          role: response.role,
          email: response.email
        }
      }
    } catch (error) {
      throw new Error(error.error || error.message || '登录失败')
    }
  },

  // 获取当前用户信息（从localStorage获取）
  async getCurrentUser() {
    try {
      const token = localStorage.getItem('token')
      const userInfo = localStorage.getItem('userInfo')
      
      if (token && userInfo) {
        return JSON.parse(userInfo)
      }
      
      throw new Error('No user info found')
    } catch (error) {
      throw new Error('获取用户信息失败')
    }
  },

  // 退出登录
  async logout() {
    try {
      // 清理本地存储
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    } catch (error) {
      // 忽略退出登录的错误
    }
  }
}

export default api