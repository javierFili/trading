import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, getToken, post, setToken, setUnauthorizedHandler, type LoginResponse, type User } from './api';

interface AuthState {
  user: User | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);

  const logout = () => {
    setToken(null);
    setUser(null);
  };

  const refresh = async () => {
    setUser(await api<User>('/api/v1/auth/me'));
  };

  useEffect(() => {
    setUnauthorizedHandler(logout);
    if (getToken()) {
      refresh().catch(logout).finally(() => setReady(true));
    } else {
      setReady(true);
    }
  }, []);

  const login = async (username: string, password: string) => {
    const res = await post<LoginResponse>('/api/v1/auth/login', { username, password });
    setToken(res.accessToken);
    setUser(res.user);
  };

  if (!ready) return null;
  return <AuthContext.Provider value={{ user, login, logout, refresh }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('AuthProvider missing');
  return ctx;
}
