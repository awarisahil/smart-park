import axios from 'axios';

const client = axios.create({ baseURL: '/api' });

function clearAuth() {
  localStorage.removeItem('token');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('user');
}

function redirectToLogin() {
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = '/login';
  }
}

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshing = null;

async function refreshTokens() {
  const refreshToken = localStorage.getItem('refreshToken');
  if (!refreshToken) {
    throw new Error('No refresh token');
  }
  // Plain axios on purpose: this call must not re-enter our interceptors.
  const response = await axios.post('/api/auth/refresh', { refreshToken });
  localStorage.setItem('token', response.data.token);
  localStorage.setItem('refreshToken', response.data.refreshToken);
}

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const { config, response } = error;
    if (!response) {
      return Promise.reject(error);
    }
    const isAuthCall = (config.url || '').includes('/auth/');
    if (response.status === 401 && !config._retried && !isAuthCall) {
      config._retried = true;
      try {
        refreshing = refreshing ?? refreshTokens().finally(() => {
          refreshing = null;
        });
        await refreshing;
        return client(config);
      } catch (refreshError) {
        clearAuth();
        redirectToLogin();
        return Promise.reject(error);
      }
    }
    if (response.status === 401 && isAuthCall) {
      clearAuth();
      redirectToLogin();
    }
    return Promise.reject(error);
  }
);

export default client;
