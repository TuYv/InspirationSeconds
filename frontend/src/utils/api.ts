import { useAuth } from '../composables/useAuth';

const apiBase = import.meta.env.VITE_API_BASE ?? '';

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const { getToken } = useAuth();
  const token = getToken();
  const headers = new Headers(init.headers as HeadersInit | undefined);
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  if (!headers.has('Content-Type') && init.body) {
    headers.set('Content-Type', 'application/json');
  }
  return fetch(`${apiBase}${path}`, { ...init, headers });
}
