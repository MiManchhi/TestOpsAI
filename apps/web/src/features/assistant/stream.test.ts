import { afterEach, expect, it, vi } from 'vitest';
import { requestResponse } from '../../app/http';
import { auth } from '../auth/session';
import { createTurnDecoder, streamTurn } from './stream';
import { answer, turn, queued } from './test-data';
import type { TurnEvent } from '../../app/contracts';
vi.mock('../../app/http', async () => ({
  ...(await vi.importActual<typeof import('../../app/http')>('../../app/http')),
  requestResponse: vi.fn(),
}));
vi.mock('../auth/session', () => ({ auth: { getAccessToken: vi.fn(() => 'fixture-token') } }));
afterEach(() => {
  vi.resetAllMocks();
  vi.mocked(auth.getAccessToken).mockReturnValue('fixture-token');
});
const started: TurnEvent = {
  event: 'turn.started',
  turnId: turn.id,
  sequence: 1,
  occurredAt: '2026-11-18T06:00:00Z',
  data: { ...queued, status: 'RUNNING' },
};
const delta: TurnEvent = {
  event: 'answer.delta',
  turnId: turn.id,
  sequence: 2,
  occurredAt: '2026-11-18T06:00:01Z',
  data: { text: '中文分片🙂' },
};
const completed: TurnEvent = {
  event: 'turn.completed',
  turnId: turn.id,
  sequence: 3,
  occurredAt: '2026-11-18T06:00:02Z',
  data: { messageId: '10000000-0000-4000-8000-000000000003', answer },
};
const frame = (event: TurnEvent) =>
  `id: ${event.sequence}\r\nevent: ${event.event}\r\ndata: ${JSON.stringify(event)}\r\n\r\n`;
it('decodes single-byte UTF-8 chunks, CRLF, heartbeats and the authoritative terminal answer', async () => {
  const bytes = new TextEncoder().encode(
    ': heartbeat\r\n\r\n' + [started, delta, completed].map(frame).join(''),
  );
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const byte of bytes) controller.enqueue(Uint8Array.of(byte));
      controller.close();
    },
  });
  vi.mocked(requestResponse).mockResolvedValue(
    new Response(body, { headers: { 'Content-Type': 'text/event-stream; charset=utf-8' } }),
  );
  const received: TurnEvent[] = [];
  await streamTurn(turn.id, 0, new AbortController().signal, (event) => received.push(event));
  expect(received).toEqual([started, delta, completed]);
  expect(vi.mocked(requestResponse).mock.calls[0]?.[0]).toBe(`/api/v2/turns/${turn.id}/events`);
  expect(vi.mocked(requestResponse).mock.calls[0]?.[2]?.headers).toEqual({
    Accept: 'text/event-stream',
    'Last-Event-ID': '0',
  });
});
it('deduplicates replayed events and rejects sequence gaps and a second terminal state', () => {
  const received: TurnEvent[] = [];
  const decoder = createTurnDecoder(turn.id, 1, (event) => received.push(event));
  decoder.push(frame(started) + frame(delta) + frame(delta));
  expect(received).toEqual([delta]);
  expect(() => decoder.push(frame({ ...completed, sequence: 4 }))).toThrow('顺序');
  const final = createTurnDecoder(turn.id, 2, () => {});
  final.push(frame(completed));
  expect(final.terminal).toBe(true);
  expect(() => final.push(frame({ ...completed, sequence: 4 }))).toThrow('顺序');
});
it('rejects mismatched IDs, event labels, foreign turns and missing started events', () => {
  expect(() =>
    createTurnDecoder(turn.id, 0, () => {}).push(frame(started).replace('id: 1', 'id: 2')),
  ).toThrow('标识');
  expect(() =>
    createTurnDecoder(turn.id, 0, () => {}).push(
      frame(started).replace('event: turn.started', 'event: answer.delta'),
    ),
  ).toThrow('标识');
  expect(() =>
    createTurnDecoder('10000000-0000-4000-8000-000000000099', 0, () => {}).push(frame(started)),
  ).toThrow('标识');
  expect(() =>
    createTurnDecoder(turn.id, 0, () => {}).push(frame({ ...delta, sequence: 1 })),
  ).toThrow('顺序');
});
it('preserves incomplete-stream failure and rejects non-SSE responses', async () => {
  vi.mocked(requestResponse).mockResolvedValueOnce(
    new Response(frame(started), { headers: { 'Content-Type': 'text/event-stream' } }),
  );
  await expect(streamTurn(turn.id, 0, new AbortController().signal, () => {})).rejects.toThrow(
    '中断',
  );
  vi.mocked(requestResponse).mockResolvedValueOnce(Response.json({}));
  await expect(streamTurn(turn.id, 0, new AbortController().signal, () => {})).rejects.toThrow(
    '事件流',
  );
});
it('does not expose old session events after token change and cancels the reader', async () => {
  const cancel = vi.fn();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(new TextEncoder().encode(frame(started)));
    },
    cancel,
  });
  vi.mocked(requestResponse).mockImplementation(async () => {
    vi.mocked(auth.getAccessToken).mockReturnValue('new-token');
    return new Response(body, { headers: { 'Content-Type': 'text/event-stream' } });
  });
  const receive = vi.fn();
  await expect(streamTurn(turn.id, 0, new AbortController().signal, receive)).rejects.toMatchObject(
    { name: 'AbortError' },
  );
  expect(receive).not.toHaveBeenCalled();
  expect(cancel).toHaveBeenCalledOnce();
});
