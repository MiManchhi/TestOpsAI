import { afterEach, expect, it, vi } from 'vitest';
import { createMemoryHistory } from 'vue-router';
import { createAppRouter, safeDestination } from './router';
import { createAuthSession } from '../features/auth/session';

afterEach(() => vi.unstubAllGlobals());
it('restores scoped destinations and removes unrelated parameters', () => {
  expect(safeDestination('/p/2/chat?runId=3&token=secret')).toBe('/p/2/chat?runId=3');
  expect(safeDestination('/p/2/settings/transfer')).toBe('/p/2/settings/transfer');
  expect(safeDestination('/p/invalid/documents')).toBe('/chat');
  expect(safeDestination('/knowledge/1')).toBe('/chat');
});
it.each(['https://example.com', '//example.com', '/login', ['/p/2/documents'], undefined])(
  'rejects unsafe or unsupported redirect %j',
  (target) => {
    expect(safeDestination(target)).toBe('/chat');
  },
);
it('preserves a protected destination through login', async () => {
  const session = createAuthSession(null);
  const router = createAppRouter(session, createMemoryHistory());
  await router.push('/p/2/documents');
  expect(router.currentRoute.value.path).toBe('/login');
  expect(router.currentRoute.value.query.redirect).toBe('/p/2/documents');
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
    await router.push('/');
    expect(router.currentRoute.value.path).toBe('/chat');
    await router.push('/login?redirect=/p/2/documents');
    expect(router.currentRoute.value.path).toBe('/p/2/documents');
    session.logout();
    await router.push('/p/2/runs');
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
    await router.push('/p/2/documents');
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
