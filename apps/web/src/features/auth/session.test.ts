import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { createAuthSession, SESSION_KEY, type AuthSession } from './session';

const user = { id: 1, username: 'example', roles: ['USER'], createdAt: '2026-11-17T00:00:00Z' };
const sessions: AuthSession[] = [];
function setup(saved?: string) {
  const values = new Map<string, string>();
  if (saved) values.set(SESSION_KEY, saved);
  const storage = {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => {
      values.set(key, value);
    },
    removeItem: (key: string) => {
      values.delete(key);
    },
  };
  const session = createAuthSession(storage);
  sessions.push(session);
  return { session, storage };
}
const saved = () =>
  JSON.stringify({ accessToken: 'sample-token', expiresAt: Date.now() + 1800_000 });
beforeEach(() => {
  vi.useFakeTimers();
});
afterEach(() => {
  sessions.forEach((session) => session.logout());
  sessions.length = 0;
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

it('restores with Java validation and never stores password or cached identity', async () => {
  const { session, storage } = setup();
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValueOnce(Response.json({ accessToken: 'sample-token', expiresIn: 1800 }))
      .mockImplementation(() => Promise.resolve(Response.json(user))),
  );
  await expect(session.signIn('example', 'sample-password')).resolves.toBe(true);
  expect(session.state.user).toEqual(user);
  const raw = storage.getItem(SESSION_KEY);
  expect(raw).not.toContain('sample-password');
  expect(raw).not.toContain('roles');
  const recovered = createAuthSession(storage);
  sessions.push(recovered);
  expect(recovered.state.user).toBeNull();
  await recovered.restore();
  expect(recovered.state.phase).toBe('authenticated');
});

it.each(['{broken', '{}', JSON.stringify({ accessToken: 'sample-token', expiresAt: 0 })])(
  'cleans invalid storage without a request',
  async (raw) => {
    const { session, storage } = setup(raw);
    const fetcher = vi.fn();
    vi.stubGlobal('fetch', fetcher);
    await session.restore();
    expect(session.state.phase).toBe('anonymous');
    expect(storage.getItem(SESSION_KEY)).toBeNull();
    expect(fetcher).not.toHaveBeenCalled();
  },
);

it('clears a rejected token on HTTP 401', async () => {
  const { session, storage } = setup(saved());
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({}, { status: 401 })));
  await session.restore();
  expect(session.state.phase).toBe('anonymous');
  expect(session.state.notice).toContain('过期');
  expect(storage.getItem(SESSION_KEY)).toBeNull();
});

it.each([403, 503])('keeps the workspace hidden on HTTP %i and allows retry', async (status) => {
  const { session, storage } = setup(saved());
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValueOnce(Response.json({}, { status }))
      .mockResolvedValue(Response.json(user)),
  );
  await session.restore();
  expect(session.state.phase).toBe('unavailable');
  expect(session.state.user).toBeNull();
  expect(storage.getItem(SESSION_KEY)).not.toBeNull();
  await session.retry();
  expect(session.state.phase).toBe('authenticated');
});

it('retains credentials during a transient network outage', async () => {
  const { session } = setup(saved());
  vi.stubGlobal(
    'fetch',
    vi.fn().mockRejectedValueOnce(new TypeError('offline')).mockResolvedValue(Response.json(user)),
  );
  await session.restore();
  expect(session.state.phase).toBe('unavailable');
  await session.retry();
  expect(session.state.user).toEqual(user);
});

it('expires the session at its deadline', async () => {
  const { session, storage } = setup(saved());
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json(user)));
  await session.restore();
  await vi.advanceTimersByTimeAsync(1800_000);
  expect(session.state.phase).toBe('anonymous');
  expect(session.state.user).toBeNull();
  expect(storage.getItem(SESSION_KEY)).toBeNull();
});

it('ignores late user responses after logout, even if the transport ignores abort', async () => {
  const { session, storage } = setup(saved());
  let deliver: ((response: Response) => void) | undefined;
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(
      () =>
        new Promise<Response>((resolve) => {
          deliver = resolve;
        }),
    ),
  );
  const loading = session.restore();
  session.logout();
  deliver?.(Response.json(user));
  await loading;
  expect(session.state.phase).toBe('anonymous');
  expect(session.state.user).toBeNull();
  expect(storage.getItem(SESSION_KEY)).toBeNull();
});

it('ignores a late login response after logout', async () => {
  const { session, storage } = setup();
  let deliver: ((response: Response) => void) | undefined;
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(
      () =>
        new Promise<Response>((resolve) => {
          deliver = resolve;
        }),
    ),
  );
  const loading = session.signIn('example', 'sample-password');
  session.logout();
  deliver?.(Response.json({ accessToken: 'late-token', expiresIn: 1800 }));
  await expect(loading).resolves.toBe(false);
  expect(storage.getItem(SESSION_KEY)).toBeNull();
  expect(session.state.phase).toBe('anonymous');
});

it('shares restoration and waits until identity validation finishes', async () => {
  const { session } = setup(saved());
  let deliver: ((response: Response) => void) | undefined;
  const fetcher = vi.fn().mockImplementation(
    () =>
      new Promise<Response>((resolve) => {
        deliver = resolve;
      }),
  );
  vi.stubGlobal('fetch', fetcher);
  const first = session.restore();
  const second = session.restore();
  deliver?.(Response.json(user));
  await Promise.all([first, second]);
  expect(fetcher).toHaveBeenCalledTimes(1);
  expect(session.state.phase).toBe('authenticated');
});

it('continues in memory with an explicit warning when browser storage is blocked', async () => {
  const session = createAuthSession(null);
  sessions.push(session);
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValueOnce(Response.json({ accessToken: 'sample-token', expiresIn: 1800 }))
      .mockResolvedValue(Response.json(user)),
  );
  await session.signIn('example', 'sample-password');
  expect(session.state.phase).toBe('authenticated');
  expect(session.state.storageWarning).toContain('刷新');
});

it('returns to anonymous on invalid credentials', async () => {
  const { session } = setup();
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({}, { status: 401 })));
  await expect(session.signIn('example', 'wrong')).rejects.toMatchObject({ status: 401 });
  expect(session.state.phase).toBe('anonymous');
});
