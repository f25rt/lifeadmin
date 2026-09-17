// localStorage wrapper for the JWT pair. Centralised so the axios interceptor and auth context
// read/write tokens the same way.

const ACCESS_KEY = 'lifeadmin.accessToken';
const REFRESH_KEY = 'lifeadmin.refreshToken';

export const tokenStore = {
  getAccess(): string | null {
    return localStorage.getItem(ACCESS_KEY);
  },
  getRefresh(): string | null {
    return localStorage.getItem(REFRESH_KEY);
  },
  set(access: string, refresh: string | null): void {
    localStorage.setItem(ACCESS_KEY, access);
    if (refresh) {
      localStorage.setItem(REFRESH_KEY, refresh);
    }
  },
  clear(): void {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};
