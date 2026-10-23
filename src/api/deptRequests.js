import request from '@/utils/request'

export const deptRequestApi = {
  list: (params) => request.get('/dept-requests', { params }),
  submit: (data) => request.post('/dept-requests', data),
  approve: (id) => request.put(`/dept-requests/${id}/approve`),
  reject: (id, reason) => request.put(`/dept-requests/${id}/reject`, null, { params: { reason } }),
  execute: (id) => request.put(`/dept-requests/${id}/execute`),
}
