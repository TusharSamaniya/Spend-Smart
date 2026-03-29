import axios, { AxiosError, AxiosRequestConfig } from "axios";

const baseURL = "http://localhost:8080";

const api = axios.create({
  baseURL,
});

let refreshPromise: Promise<string | null> | null = null;

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

api.interceptors.request.use((config) => {
  const token = getToken();

  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

async function refreshAccessToken(): Promise<string | null> {
  try {
    const response = await axios.post<{ accessToken: string }>(
      `${baseURL}/v1/auth/refresh`,
      {},
      {
        // withCredentials allows cookies-based refresh flows to work when applicable.
        withCredentials: true,
      }
    );

    const newToken = response.data?.accessToken;

    if (newToken) {
      setToken(newToken);
      return newToken;
    }

    return null;
  } catch (refreshError) {
    // Bubble up so auth layer can handle logout.
    return null;
  }
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as (AxiosRequestConfig & {
      _retry?: boolean;
    });

    if (error.response?.status === 401 && !originalRequest?._retry) {
      originalRequest._retry = true;

      refreshPromise = refreshPromise ?? refreshAccessToken();
      const newToken = await refreshPromise.finally(() => {
        refreshPromise = null;
      });

      if (newToken) {
        originalRequest.headers = originalRequest.headers ?? {};
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return api(originalRequest);
      }
    }

    return Promise.reject(error);
  }
);

export { api, getToken, setToken, clearToken };
