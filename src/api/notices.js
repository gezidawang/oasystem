import request from '@/utils/request'

export const noticeApi = {
  list: (params) => request.get('/notices', { params }),
  detail: (id) => request.get(`/notices/${id}`),
  unreadCount: () => request.get('/notices/unread-count'),
  publishPermission: () => request.get('/notices/publish-permission'),
  create: (data, draft = false) => request.post('/notices', data, { params: { draft } }),
  update: (id, data) => request.put(`/notices/${id}`, data),
  approve: (id) => request.put(`/notices/${id}/approve`),
  reject: (id, reason) => request.put(`/notices/${id}/reject`, null, { params: { reason } }),
  revoke: (id) => request.put(`/notices/${id}/revoke`),
  setTop: (id, top) => request.put(`/notices/${id}/top`, null, { params: { top } }),
  delete: (id) => request.delete(`/notices/${id}`),
}
