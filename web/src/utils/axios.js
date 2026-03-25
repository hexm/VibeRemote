import axios from 'axios'
import { message } from 'antd'

const clearAuthStorage = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('userInfo')
}

// 配置axios拦截器
axios.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

axios.interceptors.response.use(
  response => response,
  error => {
    if (error.response) {
      const { status, data } = error.response
      
      // 权限不足
      if (status === 403) {
        const errorMessage = data.message || '权限不足'
        message.error(errorMessage)
        window.dispatchEvent(new CustomEvent('auth:forbidden', {
          detail: { message: errorMessage }
        }))
        return Promise.reject(new Error(errorMessage))
      }
      
      // 未认证
      if (status === 401) {
        clearAuthStorage()
        window.dispatchEvent(new CustomEvent('auth:unauthorized'))
        return Promise.reject(new Error('未认证'))
      }
      
      // 其他错误
      if (data && data.message) {
        return Promise.reject(new Error(data.message))
      }
    }
    
    return Promise.reject(error)
  }
)

export default axios
