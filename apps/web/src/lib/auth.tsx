"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useRouter } from "next/navigation";

import {
  clearRefreshToken,
  clearToken,
  getToken,
  setRefreshToken,
  setToken,
} from "./api";

type User = {
  userId: string;
  orgId: string;
  email: string;
  role: string;
};

type AuthContextValue = {
  user: User | null;
  isAuthenticated: boolean;
  isReady: boolean;
  login: (token: string, refreshToken?: string, userOverride?: User | null) => void;
  logout: () => void;
};

type JwtPayload = Partial<User> & {
  exp?: number;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

const parseJwt = (token: string): JwtPayload | null => {
  try {
    const base64Url = token.split(".")[1];
    if (!base64Url) return null;

    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split("")
        .map((c) => `%${("00" + c.charCodeAt(0).toString(16)).slice(-2)}`)
        .join("")
    );

    return JSON.parse(jsonPayload);
  } catch (error) {
    return null;
  }
};

const isExpired = (payload: JwtPayload | null): boolean => {
  if (!payload?.exp) return false;
  return payload.exp * 1000 < Date.now();
};

const payloadToUser = (payload: JwtPayload): User | null => {
  const { userId, orgId, email, role } = payload;

  if (userId && orgId && email && role) {
    return { userId, orgId, email, role };
  }

  return null;
};

function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [user, setUser] = useState<User | null>(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isReady, setIsReady] = useState(false);

  const logout = useCallback(() => {
    clearToken();
    clearRefreshToken();
    setUser(null);
    setIsAuthenticated(false);
    router.push("/login");
  }, [router]);

  const restoreSession = useCallback(() => {
    const token = getToken();
    if (!token) {
      setIsAuthenticated(false);
      setIsReady(true);
      return;
    }

    const payload = parseJwt(token);
    if (!payload || isExpired(payload)) {
      logout();
      setIsReady(true);
      return;
    }

    const nextUser = payloadToUser(payload);
    setUser(nextUser);
    setIsAuthenticated(true);
    setIsReady(true);
  }, [logout]);

  const login = useCallback(
    (token: string, refreshToken?: string, userOverride?: User | null) => {
      const payload = parseJwt(token);

      if (!payload || isExpired(payload)) {
        logout();
        return;
      }

      setToken(token);
      if (refreshToken) {
        setRefreshToken(refreshToken);
      }
      const nextUser = userOverride ?? payloadToUser(payload);
      setUser(nextUser);
      setIsAuthenticated(true);
      setIsReady(true);
    },
    [logout]
  );

  useEffect(() => {
    restoreSession();
  }, [restoreSession]);

  const value = useMemo(
    () => ({
      user,
      isAuthenticated,
      login,
      logout,
      isReady,
    }),
    [isAuthenticated, isReady, login, logout, user]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}

export type { User };
export { AuthProvider, useAuth };
export default AuthProvider;
