import { api } from './client.js';

export const inventoryApi = {
  getByWarehouse: (warehouseId) => api.get(`/inventory/warehouse/${warehouseId}`),
  getBalance: (warehouseId, productId) =>
    api.get(`/inventory/balance?warehouseId=${warehouseId}&productId=${productId}`),
  adjust: (adjustmentData) => api.post('/inventory/adjust', adjustmentData),
  getMovements: (page = 0, size = 20) => api.get(`/inventory/movements?page=${page}&size=${size}`),
};