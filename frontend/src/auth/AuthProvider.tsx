import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { fetchCurrentUser, logout as logoutApi } from '../api/auth';
import { tokenStore } from '../api/tokenStore';
import type { CurrentUser, TokenResponse } from '../api/types';
import { AuthContext, type AuthState } from './AuthContext';

/**
 * Holds the authenticated session. On mount, if an access token is stored it validates it via
 * {@code /auth/me}; failure clears the session. Login responses are persisted and the current user
 * loaded so the app can render authenticated views.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [initializing, setInitializing] = useState(true);

  useEffect(() => {
    let active = true;
    if (!tokenStore.getAccess()) {
      setInitializing(false);
      return;
    }
    fetchCurrentUser()
      .then((me) => active && setUser(me))
      .catch(() => {
        tokenStore.clear();
        if (active) setUser(null);
      })
      .finally(() => active && setInitializing(false));
    return () => {
      active = false;
    };
  }, []);

  const onLoggedIn = useCallback(async (tokens: TokenResponse) => {
    tokenStore.set(tokens.accessToken, tokens.refreshToken);
    setUser(await fetchCurrentUser());
  }, []);

  const logout = useCallback(async () => {
    const refresh = tokenStore.getRefresh();
    if (refresh) {
      try {
        await logoutApi(refresh);
      } catch {
        // best-effort; clear locally regardless
      }
    }
    tokenStore.clear();
    setUser(null);
  }, []);

  const value = useMemo<AuthState>(
    () => ({ user, initializing, isAuthenticated: user !== null, onLoggedIn, logout }),
    [user, initializing, onLoggedIn, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
