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
  // Handle both camelCase and snake_case fields from JWT
  const userId = (payload as any).userId ?? (payload as any).user_id;
  const orgId = (payload as any).orgId ?? (payload as any).org_id;
  const email = (payload as any).email ?? (payload as any).sub;
  const role = (payload as any).role;

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
    if (typeof window !== "undefined") {
      localStorage.removeItem("accessToken");
      localStorage.removeItem("refreshToken");
      localStorage.removeItem("user");
    }
    setUser(null);
    setIsAuthenticated(false);
    router.push("/login");
  }, [router]);

  const restoreSession = useCallback(() => {
    if (typeof window === "undefined") {
      setIsReady(true);
      return;
    }

    const token = localStorage.getItem("accessToken");
    const storedUserRaw = localStorage.getItem("user");
    const storedUser: User | null = storedUserRaw ? JSON.parse(storedUserRaw) : null;

    if (!token) {
      setUser(storedUser);
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

    const userFromToken = payloadToUser(payload);
    const nextUser = storedUser ?? userFromToken;

    if (!nextUser) {
      // Token exists but doesn't carry required fields; treat as unauthenticated.
      setIsAuthenticated(false);
      setUser(null);
      setIsReady(true);
      return;
    }

    setUser(nextUser);
    setIsAuthenticated(true);
    setIsReady(true);
  }, [logout]);

  useEffect(() => {
    restoreSession();
  }, [restoreSession]);

  const value = useMemo(
    () => ({
      user,
      isAuthenticated,
      logout,
      isReady,
    }),
    [isAuthenticated, isReady, logout, user]
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
