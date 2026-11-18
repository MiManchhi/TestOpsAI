import { readonly, reactive } from 'vue';
import { AuthError, getCurrentUser, login, type CurrentUser } from './api';

export const SESSION_KEY = 'testops.auth.v1';
type SessionStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;
type Credentials = { accessToken: string; expiresAt: number };
type Phase = 'anonymous' | 'checking' | 'authenticated' | 'unavailable';

function browserStorage(): SessionStorage | null {
  try {
    return typeof window === 'undefined' ? null : window.sessionStorage;
  } catch {
    return null;
  }
}

export function createAuthSession(storage: SessionStorage | null = browserStorage()) {
  const state = reactive({
    phase: 'anonymous' as Phase,
    user: null as CurrentUser | null,
    notice: '',
    error: '',
    requestId: '',
    storageWarning: '',
  });
  let credentials: Credentials | null = null;
  let controller: AbortController | null = null;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let generation = 0;
  let initialized = false;
  let restoration: Promise<void> | null = null;

  function persist() {
    try {
      if (!storage) throw new Error('Storage unavailable');
      if (credentials) storage.setItem(SESSION_KEY, JSON.stringify(credentials));
      else storage.removeItem(SESSION_KEY);
    } catch {
      state.storageWarning = '浏览器未允许保存会话，刷新页面后需要重新登录。';
    }
  }

  function logout(notice = '') {
    generation++;
    controller?.abort();
    controller = null;
    clearTimeout(timer);
    credentials = null;
    initialized = true;
    restoration = null;
    state.phase = 'anonymous';
    state.user = null;
    state.error = '';
    state.requestId = '';
    state.notice = notice;
    persist();
  }

  function checkExpiry() {
    if (credentials && credentials.expiresAt <= Date.now()) logout('登录已过期，请重新登录。');
  }

  function scheduleExpiry() {
    clearTimeout(timer);
    if (credentials)
      timer = setTimeout(
        () => logout('登录已过期，请重新登录。'),
        Math.max(0, credentials.expiresAt - Date.now()),
      );
  }

  function begin() {
    generation++;
    controller?.abort();
    controller = new AbortController();
    state.phase = 'checking';
    state.user = null;
    state.error = '';
    state.requestId = '';
    return { version: generation, signal: controller.signal };
  }

  async function verify(version: number, signal: AbortSignal) {
    if (!credentials) return;
    try {
      const user = await getCurrentUser(credentials.accessToken, signal);
      if (generation !== version) return;
      checkExpiry();
      if (!credentials) return;
      state.user = user;
      state.phase = 'authenticated';
    } catch (error) {
      if (generation !== version) return;
      if (error instanceof AuthError && error.status === 401) {
        logout('登录已过期，请重新登录。');
        return;
      }
      state.phase = 'unavailable';
      state.error = error instanceof AuthError ? error.message : '暂时无法验证登录，请重试。';
      state.requestId = error instanceof AuthError ? error.requestId : '';
    }
  }

  async function signIn(username: string, password: string): Promise<boolean> {
    const { version, signal } = begin();
    const startedAt = Date.now();
    initialized = true;
    state.notice = '';
    try {
      const result = await login(username, password, signal);
      if (generation !== version) return false;
      credentials = {
        accessToken: result.accessToken,
        expiresAt: startedAt + result.expiresIn * 1000,
      };
      persist();
      scheduleExpiry();
      checkExpiry();
      if (!credentials) return false;
      await verify(version, signal);
      return generation === version && state.phase !== 'anonymous';
    } catch (error) {
      if (generation !== version) return false;
      logout();
      throw error;
    }
  }

  async function restore() {
    if (restoration) return restoration;
    if (initialized) {
      checkExpiry();
      return;
    }
    initialized = true;
    try {
      const raw = storage?.getItem(SESSION_KEY);
      if (!raw) return;
      const saved: unknown = JSON.parse(raw);
      if (
        typeof saved !== 'object' ||
        saved === null ||
        !('accessToken' in saved) ||
        typeof saved.accessToken !== 'string' ||
        !saved.accessToken.trim() ||
        !('expiresAt' in saved) ||
        typeof saved.expiresAt !== 'number' ||
        !Number.isSafeInteger(saved.expiresAt) ||
        saved.expiresAt <= Date.now() ||
        saved.expiresAt > Date.now() + 86400_000
      ) {
        logout('请重新登录以继续。');
        return;
      }
      credentials = { accessToken: saved.accessToken, expiresAt: saved.expiresAt };
    } catch {
      logout('请重新登录以继续。');
      return;
    }
    scheduleExpiry();
    const { version, signal } = begin();
    restoration = verify(version, signal);
    await restoration;
    restoration = null;
  }

  async function retry() {
    checkExpiry();
    if (!credentials) return;
    const { version, signal } = begin();
    await verify(version, signal);
  }

  function getAccessToken(): string | null {
    checkExpiry();
    return state.phase === 'authenticated' ? (credentials?.accessToken ?? null) : null;
  }

  return { state: readonly(state), signIn, restore, retry, logout, checkExpiry, getAccessToken };
}

export type AuthSession = ReturnType<typeof createAuthSession>;
export const auth = createAuthSession();
