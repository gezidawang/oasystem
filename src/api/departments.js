import request from '@/utils/request'

export const departmentApi = {
  tree: () => request.get('/departments/tree'),
  create: (data) => request.post('/departments', data),
  update: (id, data) => request.put(`/departments/${id}`, data),
  disable: (id) => request.put(`/departments/${id}/disable`),
  enable: (id) => request.put(`/departments/${id}/enable`),
  delete: (id) => request.delete(`/departments/${id}`),
}
