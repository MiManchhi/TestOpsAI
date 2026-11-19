import { afterEach, expect, it, vi } from 'vitest';
import { ApiError } from '../../app/http';
import * as api from './api';
import { createConversationState } from './conversation';
import {
  answer,
  assistantMessage,
  context,
  conversation,
  messagePage,
  queued,
  turn,
  userMessage,
} from './test-data';
import type { TurnEvent } from '../../app/contracts';
import type { streamTurn } from './stream';
afterEach(() => vi.useRealTimers());
function setup() {
  const deps = {
    ...api,
    getConversation: vi.fn().mockResolvedValue(conversation),
    listMessages: vi.fn().mockResolvedValue(messagePage([])),
    sendMessage: vi.fn().mockResolvedValue(queued),
    getTurn: vi.fn().mockResolvedValue(turn),
    cancelTurn: vi.fn().mockResolvedValue({ ...turn, status: 'CANCELLED' }),
    streamTurn: vi.fn<typeof streamTurn>(async () => {
      await new Promise<void>(() => {});
    }),
  };
  const model = createConversationState(deps);
  model.reset('1');
  return { model, deps };
}
const event = (sequence: number, data: TurnEvent) => ({ ...data, sequence });
const start: TurnEvent = {
  event: 'turn.started',
  turnId: turn.id,
  sequence: 1,
  occurredAt: '2026-11-18T00:00:00Z',
  data: { ...queued, status: 'RUNNING' },
};
it('restores an already completed idempotent reply from a different message page', async () => {
  const { model, deps } = setup();
  await model.load(conversation.id);
  deps.sendMessage.mockResolvedValue(turn);
  deps.listMessages
    .mockResolvedValueOnce({ ...messagePage([userMessage]), total: 21 })
    .mockResolvedValue({ items: [assistantMessage], page: 1, size: 20, total: 21 });
  await model.send('question', context);
  expect(model.state.answer).toEqual(answer);
  expect(deps.streamTurn).not.toHaveBeenCalled();
  model.reset();
});
it('suppresses a late conversation response after changing project', async () => {
  const { model, deps } = setup();
  let resolve: (value: unknown) => void = () => {};
  deps.getConversation.mockImplementation(
    () =>
      new Promise((finish) => {
        resolve = finish;
      }),
  );
  const loading = model.load(conversation.id);
  model.reset('2');
  resolve(conversation);
  await loading;
  expect(model.state.projectId).toBe('2');
  expect(model.state.conversation).toBeNull();
  expect(model.state.messages).toEqual([]);
});
it('retries a failed message with the same clientMessageId/key and never silently creates a second question', async () => {
  const { model, deps } = setup();
  await model.load(conversation.id);
  deps.sendMessage.mockRejectedValueOnce(new ApiError('offline')).mockResolvedValueOnce(turn);
  expect(await model.send('same question', context)).toBe(false);
  expect(await model.send('same question', context)).toBe(true);
  expect(deps.sendMessage.mock.calls[0]?.[1].clientMessageId).toBe(
    deps.sendMessage.mock.calls[1]?.[1].clientMessageId,
  );
  expect(deps.sendMessage.mock.calls[0]?.[2]).toBe(deps.sendMessage.mock.calls[1]?.[2]);
  expect(deps.streamTurn).not.toHaveBeenCalled();
  model.reset();
});
it('reconnects from the last applied sequence without reposting the message', async () => {
  vi.useFakeTimers();
  const { model, deps } = setup();
  await model.load(conversation.id);
  deps.streamTurn
    .mockImplementationOnce(async (_id, _after, _signal, receive) => {
      receive(start);
      receive({
        event: 'answer.delta',
        turnId: turn.id,
        sequence: 2,
        occurredAt: start.occurredAt,
        data: { text: 'partial' },
      });
      throw new ApiError('offline');
    })
    .mockImplementationOnce(async (_id, _after, _signal, receive) => {
      receive({
        event: 'turn.completed',
        turnId: turn.id,
        sequence: 3,
        occurredAt: start.occurredAt,
        data: { messageId: assistantMessage.id, answer },
      });
    });
  await model.send('question', context);
  await vi.advanceTimersByTimeAsync(600);
  expect(deps.streamTurn.mock.calls[1]?.[1]).toBe(2);
  expect(deps.sendMessage).toHaveBeenCalledOnce();
  expect(model.state.answer).toEqual(answer);
  expect(model.state.delta).toBe('');
  model.reset();
});
it('uses persisted final answer when completion wins a cancellation race', async () => {
  const { model, deps } = setup();
  await model.load(conversation.id);
  await model.send('question', context);
  deps.cancelTurn.mockResolvedValue(turn);
  deps.listMessages.mockResolvedValue(messagePage());
  await model.stop();
  expect(model.state.turn?.status).toBe('COMPLETED');
  expect(model.state.answer).toEqual(answer);
  expect(model.state.connected).toBe(false);
  expect(deps.sendMessage).toHaveBeenCalledOnce();
  model.reset();
});
it('recovers expired replay from server records without resending', async () => {
  const { model, deps } = setup();
  await model.load(conversation.id);
  deps.streamTurn.mockRejectedValue(new ApiError('expired', 409, 'EVENT_REPLAY_EXPIRED'));
  deps.listMessages
    .mockResolvedValueOnce(messagePage([userMessage]))
    .mockResolvedValue(messagePage());
  await model.send('question', context);
  await vi.waitFor(() => expect(model.state.connected).toBe(false));
  expect(model.state.answer).toEqual(answer);
  expect(model.state.notice).toContain('未重复发送');
  expect(deps.sendMessage).toHaveBeenCalledOnce();
  model.reset();
});
it('hides messages and pending content when permissions are revoked midstream', async () => {
  const { model, deps } = setup();
  await model.load(conversation.id);
  deps.streamTurn.mockImplementation(async (_id, _after, _signal, receive) => {
    receive(start);
    receive(
      event(2, {
        event: 'turn.cancelled',
        turnId: turn.id,
        sequence: 2,
        occurredAt: start.occurredAt,
        data: { reason: 'AUTH_REVOKED' },
      }),
    );
  });
  await model.send('question', context);
  await vi.waitFor(() => expect(model.state.conversation).toBeNull());
  expect(model.state.messages).toEqual([]);
  expect(model.state.answer).toBeNull();
  expect(model.state.error).toContain('权限');
  model.reset();
});
