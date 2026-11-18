import { afterEach, expect, it, vi } from 'vitest';
import { auth } from '../features/auth/session';
import { requestJSON } from './http';
vi.mock('../features/auth/session', () => ({
  auth: { getAccessToken: vi.fn(() => 'sample-session'), logout: vi.fn() },
}));
afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
  vi.mocked(auth.getAccessToken).mockReturnValue('sample-session');
});
it('authenticates business requests and clears an expired session on 401', async () => {
  const fetch = vi
    .fn()
    .mockResolvedValue(
      Response.json(
        { code: 'UNAUTHORIZED', message: 'expired', requestId: 'request-1' },
        { status: 401 },
      ),
    );
  vi.stubGlobal('fetch', fetch);
  await expect(requestJSON('/api/v2/projects', new AbortController().signal)).rejects.toMatchObject(
    { status: 401, requestId: 'request-1' },
  );
  expect(fetch.mock.calls[0]?.[1].headers.Authorization).toBe('Bearer sample-session');
  expect(auth.logout).toHaveBeenCalledOnce();
});
it('does not log out a new session when an old response arrives', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation(async () => {
      vi.mocked(auth.getAccessToken).mockReturnValue('new-session');
      return Response.json({}, { status: 401 });
    }),
  );
  await expect(requestJSON('/api/v2/projects', new AbortController().signal)).rejects.toMatchObject(
    { name: 'AbortError' },
  );
  expect(auth.logout).not.toHaveBeenCalled();
});
it('preserves 403 and selection-limit errors and never supplies sample data', async () => {
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValueOnce(Response.json({ code: 'FORBIDDEN', requestId: 'r1' }, { status: 403 }))
      .mockResolvedValueOnce(Response.json({ code: 'SELECTION_TOO_LARGE' }, { status: 422 }))
      .mockRejectedValueOnce(new TypeError('offline')),
  );
  await expect(requestJSON('/api/v2/projects', new AbortController().signal)).rejects.toMatchObject(
    { status: 403 },
  );
  await expect(requestJSON('/api/v2/projects', new AbortController().signal)).rejects.toThrow(
    '1000',
  );
  await expect(requestJSON('/api/v2/projects', new AbortController().signal)).rejects.toThrow(
    '连接',
  );
  expect(auth.logout).not.toHaveBeenCalled();
});
