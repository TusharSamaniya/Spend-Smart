import axios, { AxiosError, AxiosRequestConfig } from "axios";

const baseURL = "http://localhost:8080";

const api = axios.create({
  baseURL,
});

const getToken = (): string | null =>
  typeof window === "undefined" ? null : localStorage.getItem("accessToken");

const setToken = (token: string): void => {
  if (typeof window === "undefined") return;
  localStorage.setItem("accessToken", token);
};

const clearToken = (): void => {
  if (typeof window === "undefined") return;
  localStorage.removeItem("accessToken");
};

const getRefreshToken = (): string | null =>
  typeof window === "undefined" ? null : localStorage.getItem("refreshToken");

const setRefreshToken = (token: string): void => {
  if (typeof window === "undefined") return;
  localStorage.setItem("refreshToken", token);
};

const clearRefreshToken = (): void => {
  if (typeof window === "undefined") return;
  localStorage.removeItem("refreshToken");
};

const clearSessionAndRedirectToLogin = (): void => {
  if (typeof window === "undefined") return;
  clearToken();
  clearRefreshToken();
  localStorage.removeItem("user");
  window.location.assign("/login");
};

api.interceptors.request.use((config) => {
  const token = getToken();

  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as (AxiosRequestConfig & {
      _retry?: boolean;
    });

    if (error.response?.status === 401 && !originalRequest?._retry) {
      originalRequest._retry = true;

      const refreshToken = getRefreshToken();
      if (!refreshToken) {
        clearSessionAndRedirectToLogin();
        return Promise.reject(error);
      }

      try {
        const refreshResponse = await axios.post<{ accessToken: string }>(
          `${baseURL}/v1/auth/refresh`,
          { refreshToken }
        );

        const newAccessToken = refreshResponse.data?.accessToken;
        if (!newAccessToken) {
          clearSessionAndRedirectToLogin();
          return Promise.reject(error);
        }

        setToken(newAccessToken);
        originalRequest.headers = originalRequest.headers ?? {};
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        clearSessionAndRedirectToLogin();
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export { api, getToken, setToken, clearToken };
export { getRefreshToken, setRefreshToken, clearRefreshToken };
