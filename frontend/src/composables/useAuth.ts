const JWT_KEY = 'wx_jwt';

export type JwtPayload = {
  sub: string;
  iat: number;
  exp: number;
};

function parseJwt(token: string): JwtPayload | null {
  try {
    const base64 = token.split('.')[1];
    const json = atob(base64.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}

export function useAuth() {
  function getToken(): string | null {
    return localStorage.getItem(JWT_KEY);
  }

  function setToken(token: string): void {
    localStorage.setItem(JWT_KEY, token);
  }

  function removeToken(): void {
    localStorage.removeItem(JWT_KEY);
  }

  function isLoggedIn(): boolean {
    const token = getToken();
    if (!token) return false;
    const payload = parseJwt(token);
    if (!payload) return false;
    return payload.exp * 1000 > Date.now();
  }

  function getOpenId(): string | null {
    const token = getToken();
    if (!token) return null;
    const payload = parseJwt(token);
    return payload?.sub ?? null;
  }

  function logout(): void {
    removeToken();
  }

  return { getToken, setToken, removeToken, isLoggedIn, getOpenId, logout };
}
