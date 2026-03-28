import axios, { AxiosError, AxiosHeaders, InternalAxiosRequestConfig } from 'axios';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';
const REFRESH_ENDPOINT = '/auth/refresh';

const baseURL = Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080';

type RetriableRequestConfig = InternalAxiosRequestConfig & {
  _retry?: boolean;
};

export const apiClient = axios.create({
  baseURL,
  timeout: 15000,
});

const refreshClient = axios.create({
  baseURL,
  timeout: 15000,
});

apiClient.interceptors.request.use(async (config) => {
  const accessToken = await SecureStore.getItemAsync(ACCESS_TOKEN_KEY);

  if (accessToken) {
    if (!config.headers) {
      config.headers = new AxiosHeaders();
    }

    config.headers.set('Authorization', `Bearer ${accessToken}`);
  }

  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as RetriableRequestConfig | undefined;

    if (!originalRequest) {
      return Promise.reject(error);
    }

    const isUnauthorized = error.response?.status === 401;
    const isRefreshRequest = originalRequest.url?.includes(REFRESH_ENDPOINT) ?? false;

    if (!isUnauthorized || originalRequest._retry || isRefreshRequest) {
      return Promise.reject(error);
    }

    originalRequest._retry = true;

    try {
      const refreshToken = await SecureStore.getItemAsync(REFRESH_TOKEN_KEY);

      if (!refreshToken) {
        return Promise.reject(error);
      }

      const refreshResponse = await refreshClient.post(REFRESH_ENDPOINT, {
        refreshToken,
      });

      const nextAccessToken: string | undefined =
        refreshResponse.data?.accessToken ??
        refreshResponse.data?.access_token ??
        refreshResponse.data?.token;

      const nextRefreshToken: string | undefined =
        refreshResponse.data?.refreshToken ??
        refreshResponse.data?.refresh_token;

      if (!nextAccessToken) {
        return Promise.reject(error);
      }

      await SecureStore.setItemAsync(ACCESS_TOKEN_KEY, nextAccessToken);

      if (nextRefreshToken) {
        await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, nextRefreshToken);
      }

      if (!originalRequest.headers) {
        originalRequest.headers = new AxiosHeaders();
      }

      originalRequest.headers.set('Authorization', `Bearer ${nextAccessToken}`);

      return apiClient(originalRequest);
    } catch (refreshError) {
      await SecureStore.deleteItemAsync(ACCESS_TOKEN_KEY);
      await SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
      return Promise.reject(refreshError);
    }
  },
);

export const tokenStorage = {
  accessTokenKey: ACCESS_TOKEN_KEY,
  refreshTokenKey: REFRESH_TOKEN_KEY,
};
