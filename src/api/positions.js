import request from '@/utils/request'

export const positionApi = {
  list: () => request.get('/positions'),
  create: (data) => request.post('/positions', data),
  update: (id, data) => request.put(`/positions/${id}`, data),
  delete: (id) => request.delete(`/positions/${id}`),
}
