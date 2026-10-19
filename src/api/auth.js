import request from '@/utils/request'

// 认证相关接口（request 的 baseURL 已是 /api）
export const authApi = {
  // 返回统一 Result：{ success, code, message, data:{ token, user, permissions } }
  login: (payload) => request.post('/auth/login', payload),
  me: () => request.get('/auth/me'),
  logout: () => request.post('/auth/logout'),
}
