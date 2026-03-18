// 脚本数据管理服务
// 提供Scripts页面和Tasks页面之间的数据共享

import api from './auth'

class ScriptService {
  constructor() {
    this.listeners = []
    this.scripts = [] // 缓存数据
  }

  // 从后端API加载数据
  async loadFromAPI(filters = {}) {
    try {
      const params = { 
        page: 0, 
        size: 1000,
        ...filters
      }
      
      const response = await api.get('/web/scripts', { params })
      this.scripts = response.content || []
      this.notifyListeners()
      return this.scripts
    } catch (error) {
      console.error('加载脚本数据失败:', error)
      throw error
    }
  }

  // 获取所有脚本
  async getAllScripts(filters = {}) {
    await this.loadFromAPI(filters)
    return [...this.scripts]
  }

  // 获取脚本（用于任务创建）
  async getScriptsForTask() {
    try {
      const response = await api.get('/web/scripts/for-task')
      return response || []
    } catch (error) {
      console.error('获取任务脚本列表失败:', error)
      return []
    }
  }

  // 添加脚本（手动录入）
  async addScript(script) {
    try {
      const response = await api.post('/web/scripts', script)
      await this.loadFromAPI() // 重新加载数据
      return response
    } catch (error) {
      console.error('创建脚本失败:', error)
      throw error
    }
  }

  // 上传脚本文件
  async uploadScript(scriptData, file) {
    try {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('name', scriptData.name)
      formData.append('type', scriptData.type)
      if (scriptData.description) {
        formData.append('description', scriptData.description)
      }

      const response = await api.post('/web/scripts/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })
      await this.loadFromAPI() // 重新加载数据
      return response
    } catch (error) {
      console.error('上传脚本失败:', error)
      throw error
    }
  }

  // 更新脚本
  async updateScript(scriptId, updates) {
    try {
      const response = await api.put(`/web/scripts/${scriptId}`, updates)
      await this.loadFromAPI() // 重新加载数据
      return response
    } catch (error) {
      console.error('更新脚本失败:', error)
      throw error
    }
  }

  // 重新上传脚本文件
  async reuploadScript(scriptId, scriptData, file) {
    try {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('name', scriptData.name)
      formData.append('type', scriptData.type)
      if (scriptData.description) {
        formData.append('description', scriptData.description)
      }

      const response = await api.post(`/web/scripts/${scriptId}/reupload`, formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })
      await this.loadFromAPI() // 重新加载数据
      return response
    } catch (error) {
      console.error('重新上传脚本失败:', error)
      throw error
    }
  }

  // 删除脚本
  async deleteScript(scriptId) {
    try {
      await api.delete(`/web/scripts/${scriptId}`)
      await this.loadFromAPI() // 重新加载数据
    } catch (error) {
      console.error('删除脚本失败:', error)
      throw error
    }
  }

  // 根据ID获取脚本
  async getScriptById(scriptId) {
    try {
      const response = await api.get(`/web/scripts/${scriptId}`)
      return response
    } catch (error) {
      console.error('获取脚本详情失败:', error)
      throw error
    }
  }

  // 获取脚本内容（响应为 { content, encrypted } 格式）
  async getScriptContent(scriptId) {
    try {
      const response = await api.get(`/web/scripts/${scriptId}/content`)
      return response  // 返回完整对象，由调用方处理解密
    } catch (error) {
      console.error('获取脚本内容失败:', error)
      throw error
    }
  }

  // 下载脚本
  async downloadScript(scriptId) {
    try {
      const token = localStorage.getItem('token')
      const response = await fetch(`${api.defaults.baseURL}/web/scripts/${scriptId}/download`, {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      })
      
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }
      
      return response
    } catch (error) {
      console.error('下载脚本失败:', error)
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
    this.listeners.forEach(callback => callback(this.scripts))
  }

  // 清除缓存（强制重新加载）
  clearCache() {
    this.scripts = []
  }
}

// 创建单例实例
const scriptService = new ScriptService()

export default scriptService