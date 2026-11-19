import { afterEach, expect, it, vi } from 'vitest';
import { requestJSON } from '../../app/http';
import { commitDraft, editDraft, getDraft } from './api';
import { draft } from '../assistant/test-data';
vi.mock('../../app/http', () => ({ requestJSON: vi.fn() }));
afterEach(() => vi.resetAllMocks());
const signal = () => new AbortController().signal;
it('patches the stored revision and consumes only the new server hash/version', async () => {
  vi.mocked(requestJSON).mockResolvedValue({
    ...draft,
    revision: draft.revision + 1,
    payloadHash: 'b'.repeat(64),
  });
  const value = await editDraft(
    '1',
    draft.id,
    { revision: draft.revision, items: draft.items },
    signal(),
  );
  expect(value.payloadHash).toBe('b'.repeat(64));
  expect(JSON.parse(String(vi.mocked(requestJSON).mock.calls[0]?.[2]?.body)).revision).toBe(
    draft.revision,
  );
  vi.mocked(requestJSON).mockResolvedValue(draft);
  await expect(
    editDraft('1', draft.id, { revision: draft.revision, items: draft.items }, signal()),
  ).rejects.toThrow('版本');
});
it('sends explicit approval IDs and the exact server hash with stable retry identity', async () => {
  vi.mocked(requestJSON).mockResolvedValue({
    draftId: draft.id,
    createdCaseIds: ['11'],
    status: 'COMMITTED',
  });
  const body = {
    revision: draft.revision,
    payloadHash: draft.payloadHash,
    selectedItemIds: [draft.items[0]!.id],
  };
  await commitDraft('1', draft.id, body, draft.id, signal());
  await commitDraft('1', draft.id, body, draft.id, signal());
  expect(JSON.parse(String(vi.mocked(requestJSON).mock.calls[0]?.[2]?.body))).toEqual(body);
  expect(vi.mocked(requestJSON).mock.calls[0]?.[2]?.headers).toMatchObject({
    'Idempotency-Key': draft.id,
  });
  expect(vi.mocked(requestJSON).mock.calls[1]?.[2]?.headers).toEqual(
    vi.mocked(requestJSON).mock.calls[0]?.[2]?.headers,
  );
});
it('accepts original committed IDs on a later replay but rejects a foreign draft', async () => {
  vi.mocked(requestJSON).mockResolvedValue({
    draftId: draft.id,
    createdCaseIds: ['11'],
    status: 'COMMITTED',
  });
  const body = {
    revision: draft.revision,
    payloadHash: draft.payloadHash,
    selectedItemIds: [draft.items[0]!.id, '10000000-0000-4000-8000-000000000099'],
  };
  expect((await commitDraft('1', draft.id, body, draft.id, signal())).createdCaseIds).toEqual([
    '11',
  ]);
  vi.mocked(requestJSON).mockResolvedValue({ ...draft, projectId: '2' });
  await expect(getDraft('1', draft.id, signal())).rejects.toThrow('项目范围');
});
