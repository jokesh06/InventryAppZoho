import { api } from './client.js';

export const warehouseApi = {
  getAll: () => api.get('/warehouses'),
  getById: (id) => api.get(`/warehouses/${id}`),
  create: (warehouseData) => api.post('/warehouses', warehouseData),
};