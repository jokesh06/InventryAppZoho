import { api } from './client.js';

export const salesOrderApi = {
  getAll: (page = 0, size = 20) => api.get(`/sales-orders?page=${page}&size=${size}`),

  create: (orderData, idempotencyKey) =>
    api.post('/sales-orders', orderData, idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),

  confirm: (id) => api.post(`/sales-orders/${id}/confirm`),

  ship: (id, shippingData) => api.post(`/sales-orders/${id}/ship`, shippingData),

  cancel: (id) => api.post(`/sales-orders/${id}/cancel`),
};