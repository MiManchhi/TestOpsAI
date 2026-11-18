import { afterEach, expect, it, vi } from 'vitest';
import { requestJSON } from '../../app/http';
import { createCaseSelection, filterQuery, getTestCase, listTestCases } from './api';
import { casePage, sampleCase, selection } from './test-data';
vi.mock('../../app/http', () => ({ requestJSON: vi.fn() }));
afterEach(() => vi.resetAllMocks());
const signal = () => new AbortController().signal;
it('sends exact exploded tags, false descendant scope and stable server pagination', async () => {
  vi.mocked(requestJSON).mockResolvedValue(casePage());
  await listTestCases(
    '1',
    { suiteId: '2', includeDescendants: false, tags: ['smoke', 'tariff'], tagsMatch: 'ALL' },
    0,
    20,
    'title,asc',
    signal(),
  );
  const url = new URL(String(vi.mocked(requestJSON).mock.calls[0]?.[0]), 'https://example.invalid');
  expect(url.pathname).toBe('/api/v2/projects/1/test-cases');
  expect(url.searchParams.getAll('tags')).toEqual(['smoke', 'tariff']);
  expect(url.searchParams.get('includeDescendants')).toBe('false');
  expect(url.searchParams.get('sort')).toBe('title,asc');
  expect(() => filterQuery({ tags: ['smoke', 'smoke'] })).toThrow();
});
it('preserves BIGINT string IDs and rejects numeric or cross-project responses', async () => {
  const id = '9223372036854775807';
  vi.mocked(requestJSON).mockResolvedValue({ ...sampleCase, id });
  expect((await getTestCase('1', id, signal())).id).toBe(id);
  vi.mocked(requestJSON).mockResolvedValue({ ...sampleCase, id: 1 });
  await expect(getTestCase('1', '1', signal())).rejects.toThrow();
  vi.mocked(requestJSON).mockResolvedValue({ ...sampleCase, projectId: '2' });
  await expect(getTestCase('1', '1', signal())).rejects.toThrow('项目范围');
});
it('rejects wrong pagination and mismatched case detail IDs', async () => {
  vi.mocked(requestJSON).mockResolvedValue(casePage(1));
  await expect(listTestCases('1', {}, 0, 20, 'title,asc', signal())).rejects.toThrow('分页');
  vi.mocked(requestJSON).mockResolvedValue({ ...sampleCase, id: '2' });
  await expect(getTestCase('1', '1', signal())).rejects.toThrow('不一致');
});
it('sends immutable FILTER selection and idempotency key, validates the returned set', async () => {
  vi.mocked(requestJSON).mockResolvedValue(selection());
  const key = crypto.randomUUID();
  await createCaseSelection(
    '1',
    { mode: 'FILTER', filter: { tags: ['smoke'] }, excludedCaseIds: ['2'] },
    key,
    signal(),
  );
  const options = vi.mocked(requestJSON).mock.calls[0]?.[2];
  expect(options?.headers).toEqual({ 'Content-Type': 'application/json', 'Idempotency-Key': key });
  expect(JSON.parse(String(options?.body))).toEqual({
    mode: 'FILTER',
    filter: { tags: ['smoke'] },
    excludedCaseIds: ['2'],
  });
  vi.mocked(requestJSON).mockResolvedValue({ ...selection(), count: 2 });
  await expect(
    createCaseSelection('1', { mode: 'EXPLICIT', caseIds: ['1'] }, key, signal()),
  ).rejects.toThrow('不完整');
  vi.mocked(requestJSON).mockResolvedValue(selection(['2']));
  await expect(
    createCaseSelection('1', { mode: 'EXPLICIT', caseIds: ['1'] }, key, signal()),
  ).rejects.toThrow('不完整');
});
