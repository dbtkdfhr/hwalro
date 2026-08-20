import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { authApi } from '../api/authApi';
import { setSessionExpiredHandler, tokenStore } from '../../../api/client';
import type { AuthResponse, User } from '../types/auth';

interface AuthContextValue {
  user: User | null;
  isInitializing: boolean;
  login: (loginId: string, password: string, rememberMe: boolean) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

let bootRefreshPromise: Promise<AuthResponse> | null = null;

function refreshSession(): Promise<AuthResponse> {
  bootRefreshPromise ??= authApi.refresh().finally(() => {
    bootRefreshPromise = null;
  });
  return bootRefreshPromise;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isInitializing, setIsInitializing] = useState(true);

  useEffect(() => {
    let active = true;
    refreshSession()
      .then((response) => {
        if (!active) return;
        tokenStore.set(response.accessToken);
        setUser(response.user);
      })
      .catch(() => {
        if (!active) return;
        tokenStore.set(null);
        setUser(null);
      })
      .finally(() => {
        if (active) setIsInitializing(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    setSessionExpiredHandler(() => {
      tokenStore.set(null);
      setUser(null);
    });
    return () => setSessionExpiredHandler(null);
  }, []);

  const login = useCallback(async (loginId: string, password: string, rememberMe: boolean) => {
    const response = await authApi.login({ loginId, password, rememberMe });
    tokenStore.set(response.accessToken);
    setUser(response.user);
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      tokenStore.set(null);
      setUser(null);
    }
  }, []);

  return (
    <AuthContext.Provider value={{ user, isInitializing, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
