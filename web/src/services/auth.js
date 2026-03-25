import axios from 'axios'
import { setSessionKey, clearSessionKey } from '../utils/crypto'

// 根据环境变量自动选择API地址
// 生产环境使用相对路径 /api（通过Nginx代理）
// 开发环境使用 http://localhost:8080/api
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

// 创建axios实例
const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
})

let hasDispatchedUnauthorized = false

const clearAuthStorage = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('userInfo')
  clearSessionKey()
}

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
      // 如果是登录接口返回401，不要重新加载页面，让登录表单显示错误
      const isLoginRequest = error.config?.url?.includes('/auth/login')
      
      if (!isLoginRequest) {
        // 非登录请求的401：统一清理本地会话，并通知App层切回登录页
        clearAuthStorage()

        if (!hasDispatchedUnauthorized) {
          hasDispatchedUnauthorized = true
          window.dispatchEvent(new CustomEvent('auth:unauthorized'))
          setTimeout(() => {
            hasDispatchedUnauthorized = false
          }, 0)
        }
      }
    }

    if (error.response?.status === 403) {
      window.dispatchEvent(new CustomEvent('auth:forbidden', {
        detail: {
          message: error.response?.data?.message || '您没有权限执行该操作'
        }
      }))
    }
    
    // 统一错误格式，确保错误对象包含有用的信息
    const errorData = error.response?.data || {}
    const errorObj = {
      status: error.response?.status,
      message: errorData.message || errorData.error || error.message || '请求失败',
      error: errorData.error || error.message,
      ...errorData
    }
    
    return Promise.reject(errorObj)
  }
)

export const authService = {
  // 登录
  async login(credentials) {
    try {
      const response = await api.post('/auth/login', credentials)
      // 存储前端通信加密密钥
      if (response.encryptionKey) {
        setSessionKey(response.encryptionKey)
      }
      return {
        token: response.token,
        user: {
          username: response.username,
          email: response.email,
          realName: response.realName,
          permissions: response.permissions || []
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
      clearAuthStorage()
    } catch (error) {
      // 忽略退出登录的错误
    }
  },

  // 刷新加密密钥（页面刷新后 sessionStorage 丢失时调用）
  async refreshEncryptionKey() {
    try {
      const token = localStorage.getItem('token')
      if (!token) return
      const response = await api.post('/auth/refresh-key')
      if (response.encryptionKey) {
        setSessionKey(response.encryptionKey)
      }
    } catch (error) {
      console.warn('[auth] 刷新加密密钥失败:', error)
    }
  }
}

export default api
