import { api } from './client.js';

export const purchaseOrderApi = {
  getAll: (page = 0, size = 50) => api.get(`/purchase-orders?page=${page}&size=${size}`),
  getById: (id) => api.get(`/purchase-orders/${id}`),

  create: (poData) => api.post('/purchase-orders', poData),

  // Changed from api.patch to api.post:
  submit: (id) => api.post(`/purchase-orders/${id}/submit`),

  receive: (id, receiveData) => api.post(`/purchase-orders/${id}/receive`, receiveData),
};