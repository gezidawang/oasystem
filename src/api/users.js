import request from '@/utils/request'

// 员工账号管理（8-3 唯一建档入口；权限码 user:manage + 职级 W3 闸门在后端）
export const userApi = {
  page: (params) => request.get('/users', { params }),
  create: (data) => request.post('/users', data),
  update: (id, data) => request.put(`/users/${id}`, data),
  setStatus: (id, status) => request.put(`/users/${id}/status`, null, { params: { status } }),
  resetPassword: (id) => request.put(`/users/${id}/reset-password`),
  unlock: (id) => request.put(`/users/${id}/unlock`),
}
