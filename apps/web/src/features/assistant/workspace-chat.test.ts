import { afterEach, expect, it, vi } from 'vitest';
import { assistantStatus, watchTurn } from './workspace-chat';
import { requestJSON, requestResponse } from '../../app/http';
vi.mock('../../app/http', () => ({ requestJSON: vi.fn(), requestResponse: vi.fn() }));
afterEach(() => vi.resetAllMocks());
const signal = new AbortController().signal;
it('reports readiness only for a valid provider state', async () => {
  vi.mocked(requestJSON)
    .mockResolvedValueOnce({ chat: 'UNCONFIGURED', embedding: 'READY' })
    .mockResolvedValueOnce({ chat: 'OK', embedding: 'OK' });
  expect(await assistantStatus('2', signal)).toEqual({ chat: 'UNCONFIGURED', embedding: 'READY' });
  await expect(assistantStatus('2', signal)).rejects.toThrow();
  expect(requestJSON).toHaveBeenCalledWith('/api/v3/projects/2/assistant-status', signal);
});
it('rejects a damaged stream without publishing a response', async () => {
  vi.mocked(requestResponse).mockResolvedValue(
    new Response('id: 1\nevent: turn.completed\ndata: {}\n\n', {
      headers: { 'Content-Type': 'text/event-stream' },
    }),
  );
  const notify = vi.fn();
  await expect(watchTurn('2', 'turn', 0, signal, notify)).rejects.toThrow();
  expect(notify).not.toHaveBeenCalled();
});
