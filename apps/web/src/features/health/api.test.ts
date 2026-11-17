import { afterEach, expect, it, vi } from 'vitest';
import { getServerHealth } from './api';

afterEach(() => vi.unstubAllGlobals());

it('uses the Java entry point', async () => {
  const request = vi.fn().mockResolvedValue(new Response('{"status":"UP"}'));
  vi.stubGlobal('fetch', request);
  await expect(getServerHealth()).resolves.toEqual({ status: 'UP' });
  expect(request.mock.calls[0]?.[0]).toBe('/actuator/health');
});

it.each([new Response('', { status: 503 }), new Response('{"status":"DOWN"}')])(
  'rejects unavailable or invalid health responses',
  async (response) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));
    await expect(getServerHealth()).rejects.toThrow();
  },
);
