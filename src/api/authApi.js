import { api } from './client.js';

export const authApi = {
  login: (credentials) => api.post('/auth/login', credentials),
};