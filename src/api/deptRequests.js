import request from '@/utils/request'

export const deptRequestApi = {
  list: (params) => request.get('/dept-requests', { params }),
  submit: (data) => request.post('/dept-requests', data),
  approve: (id) => request.put(`/dept-requests/${id}/approve`),
  reject: (id, reason) => request.put(`/dept-requests/${id}/reject`, null, { params: { reason } }),
  // 申请人撤回自己的提案（提案权延伸，仅 pending/approved 可撤）
  withdraw: (id) => request.put(`/dept-requests/${id}/withdraw`),
  execute: (id) => request.put(`/dept-requests/${id}/execute`),
}
