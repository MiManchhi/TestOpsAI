import { afterEach, expect, it, vi } from 'vitest';
import { AuthError, getCurrentUser, login } from './api';

const signal = () => new AbortController().signal;
afterEach(() => vi.unstubAllGlobals());

it('posts credentials only to Java and validates the login result', async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValue(Response.json({ accessToken: 'sample-token', expiresIn: 1800 }));
  vi.stubGlobal('fetch', fetcher);
  await expect(login('example', 'sample-password', signal())).resolves.toEqual({
    accessToken: 'sample-token',
    expiresIn: 1800,
  });
  expect(fetcher).toHaveBeenCalledWith(
    '/api/v1/auth/login',
    expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ username: 'example', password: 'sample-password' }),
    }),
  );
});

it.each([
  { accessToken: '', expiresIn: 1800 },
  { accessToken: 'sample', expiresIn: -1 },
  { accessToken: 'sample', expiresIn: '1800' },
  null,
])('rejects a malformed login response: %j', async (body) => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json(body)));
  await expect(login('example', 'sample-password', signal())).rejects.toThrow('服务响应格式异常');
});

it('sends the bearer token and validates current user fields', async () => {
  const user = { id: 1, username: 'example', roles: ['USER'], createdAt: '2026-11-17T00:00:00Z' };
  const fetcher = vi.fn().mockResolvedValue(Response.json(user));
  vi.stubGlobal('fetch', fetcher);
  await expect(getCurrentUser('sample-token', signal())).resolves.toEqual(user);
  expect(fetcher).toHaveBeenCalledWith(
    '/api/v1/users/me',
    expect.objectContaining({ headers: { Authorization: 'Bearer sample-token' } }),
  );
});

it.each([{}, { id: 1, username: 'x', roles: [4], createdAt: 'invalid' }, null])(
  'rejects invalid user data',
  async (body) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json(body)));
    await expect(getCurrentUser('sample-token', signal())).rejects.toThrow('服务响应格式异常');
  },
);

it.each([401, 403, 500])(
  'preserves HTTP %i and request ID without exposing internal messages',
  async (status) => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          Response.json(
            { message: 'private internal detail', requestId: 'sample-request' },
            { status },
          ),
        ),
    );
    await expect(getCurrentUser('sample-token', signal())).rejects.toMatchObject({
      status,
      requestId: 'sample-request',
    });
    await expect(getCurrentUser('sample-token', signal())).rejects.not.toThrow(
      'private internal detail',
    );
  },
);

it('handles proxy HTML, invalid JSON and network failure', async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValueOnce(new Response('<html>unavailable</html>', { status: 502 }))
    .mockResolvedValueOnce(new Response('not json'))
    .mockRejectedValueOnce(new TypeError('Failed to fetch'));
  vi.stubGlobal('fetch', fetcher);
  await expect(login('x', 'x', signal())).rejects.toBeInstanceOf(AuthError);
  await expect(login('x', 'x', signal())).rejects.toThrow('响应格式异常');
  await expect(login('x', 'x', signal())).rejects.toThrow('检查网络');
});

it('combines cancellation with the 10 second deadline', async () => {
  const abort = new AbortController();
  const fetcher = vi.fn(
    (_url: string, options: RequestInit) =>
      new Promise<Response>((_resolve, reject) => {
        options.signal?.addEventListener('abort', () => reject(options.signal?.reason));
      }),
  );
  const timeout = vi.spyOn(AbortSignal, 'timeout');
  vi.stubGlobal('fetch', fetcher);
  const result = login('x', 'x', abort.signal);
  const assertion = expect(result).rejects.toMatchObject({ name: 'AbortError' });
  abort.abort();
  await assertion;
  expect(timeout).toHaveBeenCalledWith(10_000);
  timeout.mockRestore();
});
