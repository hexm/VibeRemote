import axios from 'axios'
import { message } from 'antd'

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
        message.error(data.message || '权限不足')
        return Promise.reject(new Error(data.message || '权限不足'))
      }
      
      // 未认证
      if (status === 401) {
        message.error('登录已过期，请重新登录')
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        window.location.href = '/'
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
