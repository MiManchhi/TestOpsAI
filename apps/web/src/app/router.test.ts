import { afterEach, expect, it, vi } from 'vitest';
import { createMemoryHistory } from 'vue-router';
import { createAppRouter, safeDestination } from './router';
import { createAuthSession } from '../features/auth/session';

afterEach(() => vi.unstubAllGlobals());
it('preserves valid project detail destinations while dropping unrelated query parameters', () => {
  expect(safeDestination('/test-cases/9223372036854775807?projectId=1&token=secret')).toBe(
    '/test-cases/9223372036854775807?projectId=1',
  );
  expect(safeDestination('/test-cases/invalid?projectId=1')).toBe('/overview');
  expect(safeDestination('/test-cases/1?projectId=bad')).toBe('/test-cases/1');
});
it.each(['https://example.com', '//example.com', '/login', ['/knowledge'], undefined])(
  'rejects unsafe or unsupported redirect %j',
  (target) => {
    expect(safeDestination(target)).toBe('/overview');
  },
);
it('preserves a protected destination through login', async () => {
  const session = createAuthSession(null);
  const router = createAppRouter(session, createMemoryHistory());
  await router.push('/knowledge');
  expect(router.currentRoute.value.path).toBe('/login');
  expect(router.currentRoute.value.query.redirect).toBe('/knowledge');
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValueOnce(Response.json({ accessToken: 'sample-token', expiresIn: 1800 }))
      .mockResolvedValue(
        Response.json({
          id: 1,
          username: 'example',
          roles: ['USER'],
          createdAt: '2026-11-17T00:00:00Z',
        }),
      ),
  );
  try {
    await session.signIn('example', 'sample-password');
    await router.push('/overview');
    await router.push('/login?redirect=/knowledge');
    expect(router.currentRoute.value.path).toBe('/knowledge');
    session.logout();
    await router.push('/test-cases');
    expect(router.currentRoute.value.path).toBe('/login');
  } finally {
    session.logout();
  }
});

it('sends a temporary-password session to the standalone password page without exposing a business token', async () => {
  const session = createAuthSession(null);
  const router = createAppRouter(session, createMemoryHistory());
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(Response.json({ accessToken: 'temporary-token', expiresIn: 1800 }))
    .mockResolvedValueOnce(Response.json({ code: 'PASSWORD_CHANGE_REQUIRED' }, { status: 403 }))
    .mockResolvedValueOnce(Response.json({ mustChangePassword: true, enabled: true }))
    .mockResolvedValueOnce(new Response(null, { status: 204 }));
  vi.stubGlobal('fetch', fetch);
  try {
    await session.signIn('engineer', 'temporary');
    await router.push('/knowledge');
    expect(router.currentRoute.value.path).toBe('/account/password');
    expect(session.getAccessToken()).toBeNull();
    await session.updatePassword(
      'temporary',
      'replacement',
      'fixture-key',
      new AbortController().signal,
    );
    expect(fetch).toHaveBeenLastCalledWith(
      '/api/v3/account/password',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          Authorization: 'Bearer temporary-token',
          'Idempotency-Key': 'fixture-key',
        }),
      }),
    );
    expect(session.state.phase).toBe('anonymous');
  } finally {
    session.logout();
  }
});
