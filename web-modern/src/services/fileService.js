// 文件管理服务
// 提供文件上传、下载、管理等功能

import api from './auth'

class FileService {
  constructor() {
    this.listeners = []
    this.files = [] // 缓存数据
  }

  // 从后端API加载数据
  async loadFromAPI(filters = {}) {
    try {
      const params = { 
        page: 0, 
        size: 1000,
        ...filters
      }
      
      const response = await api.get('/web/files', { params })
      this.files = response.content || []
      this.notifyListeners()
      return this.files
    } catch (error) {
      console.error('加载文件数据失败:', error)
      throw error
    }
  }

  // 获取所有文件
  async getAllFiles(filters = {}) {
    await this.loadFromAPI(filters)
    return [...this.files]
  }

  // 获取文件（用于任务创建）
  async getFilesForTask() {
    try {
      const response = await api.get('/web/files/for-task')
      return response || []
    } catch (error) {
      console.error('获取任务文件列表失败:', error)
      return []
    }
  }

  // 上传文件
  async uploadFile(fileData, file) {
    try {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('name', fileData.name)
      formData.append('category', fileData.category)
      if (fileData.description) {
        formData.append('description', fileData.description)
      }
      if (fileData.tags) {
        formData.append('tags', fileData.tags)
      }
      if (fileData.version) {
        formData.append('version', fileData.version)
      }

      const response = await api.post('/web/files/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })
      await this.loadFromAPI() // 重新加载数据
      return response
    } catch (error) {
      console.error('上传文件失败:', error)
      throw error
    }
  }

  // 删除文件
  async deleteFile(fileId) {
    try {
      await api.delete(`/web/files/${fileId}`)
      await this.loadFromAPI() // 重新加载数据
    } catch (error) {
      console.error('删除文件失败:', error)
      throw error
    }
  }

  // 根据ID获取文件
  async getFileById(fileId) {
    try {
      const response = await api.get(`/web/files/${fileId}`)
      return response
    } catch (error) {
      console.error('获取文件详情失败:', error)
      throw error
    }
  }

  // 下载文件
  async downloadFile(fileId) {
    try {
      const token = localStorage.getItem('token')
      const response = await fetch(`${api.defaults.baseURL}/web/files/${fileId}/download`, {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      })
      
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }
      
      return response
    } catch (error) {
      console.error('下载文件失败:', error)
      throw error
    }
  }

  // 获取文件分类
  async getCategories() {
    try {
      const response = await api.get('/web/files/categories')
      return response || []
    } catch (error) {
      console.error('获取分类列表失败:', error)
      return []
    }
  }

  // 获取分类统计
  async getCategoryStats() {
    try {
      const response = await api.get('/web/files/stats')
      return response || []
    } catch (error) {
      console.error('获取分类统计失败:', error)
      return []
    }
  }

  // 验证文件完整性
  async verifyFile(fileId, checksum) {
    try {
      const response = await api.post(`/web/files/${fileId}/verify`, null, {
        params: { checksum }
      })
      return response
    } catch (error) {
      console.error('验证文件失败:', error)
      throw error
    }
  }

  // 添加监听器
  addListener(callback) {
    this.listeners.push(callback)
  }

  // 移除监听器
  removeListener(callback) {
    const index = this.listeners.indexOf(callback)
    if (index > -1) {
      this.listeners.splice(index, 1)
    }
  }

  // 通知所有监听器
  notifyListeners() {
    this.listeners.forEach(callback => callback(this.files))
  }

  // 清除缓存（强制重新加载）
  clearCache() {
    this.files = []
  }
}

// 创建单例实例
const fileService = new FileService()

export default fileService