import { api } from './client.js';

export const productApi = {
  getAll: (page = 0, size = 20) => api.get(`/products?page=${page}&size=${size}`),
  getById: (id) => api.get(`/products/${id}`),
  create: (productData) => api.post('/products', productData),
};