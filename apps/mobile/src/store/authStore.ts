import * as SecureStore from 'expo-secure-store';
import { create } from 'zustand';

const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';
const USER_KEY = 'authUser';

export type AuthUser = {
  userId: string;
  orgId: string;
  email: string;
  role: string;
};

type AuthState = {
  accessToken: string | null;
  refreshToken: string | null;
  user: AuthUser | null;
  isAuthenticated: boolean;
  login: (accessToken: string, refreshToken: string, user: AuthUser) => Promise<void>;
  logout: () => Promise<void>;
  loadStoredAuth: () => Promise<void>;
};

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  refreshToken: null,
  user: null,
  isAuthenticated: false,

  login: async (accessToken, refreshToken, user) => {
    await Promise.all([
      SecureStore.setItemAsync(ACCESS_TOKEN_KEY, accessToken),
      SecureStore.setItemAsync(REFRESH_TOKEN_KEY, refreshToken),
      SecureStore.setItemAsync(USER_KEY, JSON.stringify(user)),
    ]);

    set({
      accessToken,
      refreshToken,
      user,
      isAuthenticated: true,
    });
  },

  logout: async () => {
    await Promise.all([
      SecureStore.deleteItemAsync(ACCESS_TOKEN_KEY),
      SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY),
      SecureStore.deleteItemAsync(USER_KEY),
    ]);

    set({
      accessToken: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false,
    });
  },

  loadStoredAuth: async () => {
    const [accessToken, refreshToken, storedUser] = await Promise.all([
      SecureStore.getItemAsync(ACCESS_TOKEN_KEY),
      SecureStore.getItemAsync(REFRESH_TOKEN_KEY),
      SecureStore.getItemAsync(USER_KEY),
    ]);

    let user: AuthUser | null = null;

    if (storedUser) {
      try {
        user = JSON.parse(storedUser) as AuthUser;
      } catch {
        user = null;
      }
    }

    const isAuthenticated = Boolean(accessToken && refreshToken);

    set({
      accessToken,
      refreshToken,
      user,
      isAuthenticated,
    });
  },
}));
