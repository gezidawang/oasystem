import axios from 'axios'
import { ElMessage } from 'element-plus'

// 统一 axios 实例：走 vite 代理 /api -> http://localhost:3000
const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true,
})

// 请求拦截：自动携带 JWT
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截：统一解包 Result，处理 401 与滑动续期
request.interceptors.response.use(
  (response) => {
    // 滑动续期（热加载）：后端对活跃用户在新 token 剩余不足 1/3 有效期时随响应下发 X-Renew-Token，
    // 前端静默替换本地 token——正常使用永不踢线，闲置超过 token 有效期才登出
    const renew = response.headers?.['x-renew-token']
    if (renew) {
      localStorage.setItem('token', renew)
    }
    return response.data
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      // 真过期（闲置超时/被停用）才提示重登；活跃用户已被上面的续期机制接住
      ElMessage.error('登录已过期，请重新登录')
      localStorage.removeItem('token')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    } else {
      ElMessage.error(error.response?.data?.message || '请求失败')
    }
    return Promise.reject(error)
  }
)

export default request
