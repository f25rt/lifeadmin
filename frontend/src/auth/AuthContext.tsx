import { createContext, useContext } from 'react';
import type { CurrentUser, TokenResponse } from '../api/types';

export interface AuthState {
  user: CurrentUser | null;
  initializing: boolean;
  isAuthenticated: boolean;
  /** Persist tokens from a login response and load the current user. */
  onLoggedIn: (tokens: TokenResponse) => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthState | undefined>(undefined);

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
