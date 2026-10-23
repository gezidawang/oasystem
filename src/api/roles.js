import request from '@/utils/request'

export const roleApi = {
  list: () => request.get('/roles'),
  create: (data) => request.post('/roles', data),
  update: (id, data) => request.put(`/roles/${id}`, data),
  delete: (id) => request.delete(`/roles/${id}`),
  allPermissions: () => request.get('/roles/permissions'),
  rolePermissions: (id) => request.get(`/roles/${id}/permissions`),
  savePermissions: (id, permIds) => request.put(`/roles/${id}/permissions`, permIds),
}
