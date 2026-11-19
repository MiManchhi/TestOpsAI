import { afterEach, expect, it, vi } from 'vitest';
import { requestJSON } from '../../app/http';
import { createProject, getProject, listMembers, removeMember, setMember } from './api';
import { sampleProject } from '../test-cases/test-data';
vi.mock('../../app/http', () => ({ requestJSON: vi.fn() }));
afterEach(() => vi.resetAllMocks());
const signal = () => new AbortController().signal;
it('validates creation fields and sends a stable idempotency key', async () => {
  vi.mocked(requestJSON).mockResolvedValue(sampleProject);
  await createProject(
    { key: 'METER_DEMO', name: 'Demo' },
    '00000000-0000-4000-8000-000000000001',
    signal(),
  );
  expect(vi.mocked(requestJSON).mock.calls[0]?.[2]?.headers).toMatchObject({
    'Idempotency-Key': '00000000-0000-4000-8000-000000000001',
  });
  await expect(createProject({ key: 'invalid', name: '' }, 'key', signal())).rejects.toThrow();
});
it('rejects mismatched project responses and bare member arrays', async () => {
  vi.mocked(requestJSON).mockResolvedValue({ ...sampleProject, id: '2' });
  await expect(getProject('1', signal())).rejects.toThrow('范围');
  vi.mocked(requestJSON).mockResolvedValue([]);
  await expect(listMembers('1', 0, signal())).rejects.toThrow('约定');
});
it('does not offer ownership transfer and validates user/role response binding', async () => {
  vi.mocked(requestJSON).mockResolvedValue({ userId: '3', username: 'demo', role: 'VIEWER' });
  await expect(setMember('1', '2', { role: 'VIEWER' }, signal())).rejects.toThrow('不匹配');
  await expect(
    setMember('1', '2', { role: 'OWNER' } as unknown as { role: 'VIEWER' }, signal()),
  ).rejects.toThrow('约定');
});
it('treats 204 removal as successful and preserves server errors', async () => {
  vi.mocked(requestJSON).mockResolvedValue(null);
  await removeMember('1', '2', signal());
  expect(vi.mocked(requestJSON).mock.calls[0]?.[2]?.method).toBe('DELETE');
  vi.mocked(requestJSON).mockRejectedValue(new Error('LAST_OWNER'));
  await expect(removeMember('1', '2', signal())).rejects.toThrow('LAST_OWNER');
});
