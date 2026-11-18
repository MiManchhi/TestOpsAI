import { afterEach, expect, it, vi } from 'vitest';
import examples from '../../../../../docs/contracts/examples.json';
import { readContract } from '../../app/contracts';
import { requestJSON } from '../../app/http';
import { getArtifactLines, getRun, listArtifacts, listResults, listRuns } from './api';
vi.mock('../../app/http', () => ({ requestJSON: vi.fn() }));
afterEach(() => vi.resetAllMocks());
const signal = () => new AbortController().signal;
const run = readContract('Run', examples.instances.find((item) => item.schema === 'Run')?.data);
it('keeps BIGINT IDs and rejects mismatched project, run and inconsistent counts', async () => {
  const id = '9223372036854775807';
  vi.mocked(requestJSON).mockResolvedValue({ ...run, id });
  expect((await getRun(run.projectId, id, signal())).id).toBe(id);
  vi.mocked(requestJSON).mockResolvedValue({ ...run, projectId: '999' });
  await expect(getRun(run.projectId, run.id, signal())).rejects.toThrow('项目范围');
  vi.mocked(requestJSON).mockResolvedValue({
    ...run,
    counts: { total: 99, pass: 1, fail: 1, skip: 0 },
  });
  await expect(getRun(run.projectId, run.id, signal())).rejects.toThrow('统计');
});
it('sends exact result filters and refuses results from a different run', async () => {
  vi.mocked(requestJSON).mockResolvedValue({ items: [], total: 0, page: 0, size: 20 });
  await listResults('1', '2', 0, 'FAIL', signal());
  expect(vi.mocked(requestJSON).mock.calls[0]?.[0]).toBe(
    '/api/v2/projects/1/runs/2/results?page=0&size=20&status=FAIL',
  );
  const result = readContract('ResultPage', { items: [], total: 0, page: 0, size: 20 });
  const sample = examples.instances.find((item) => item.schema === 'Result')?.data;
  vi.mocked(requestJSON).mockResolvedValue({ ...result, total: 1, items: [sample] });
  await expect(listResults('1', '999', 0, '', signal())).rejects.toThrow('范围');
});
it('checks server pagination and artifact binding', async () => {
  vi.mocked(requestJSON).mockResolvedValue({ items: [], total: 0, page: 2, size: 20 });
  await expect(listRuns('1', 0, '', signal())).rejects.toThrow('分页');
  vi.mocked(requestJSON).mockResolvedValue({
    items: [
      {
        id: '1',
        runId: '3',
        kind: 'LOG_TEXT',
        name: 'safe.log',
        sizeBytes: 12,
        sha256: 'a'.repeat(64),
      },
    ],
    total: 1,
    page: 0,
    size: 20,
  });
  await expect(listArtifacts('1', '2', 0, signal())).rejects.toThrow('范围');
});
it('checks text locator identity and uses bounded line windows', async () => {
  vi.mocked(requestJSON).mockResolvedValue({
    artifactId: '3',
    fromLine: 120,
    lines: ['<script>text only</script>'],
    hasMore: false,
  });
  expect((await getArtifactLines('1', '3', 120, signal())).lines).toHaveLength(1);
  expect(vi.mocked(requestJSON).mock.calls[0]?.[0]).toBe(
    '/api/v2/projects/1/artifacts/3/lines?fromLine=120&limit=100',
  );
  await expect(getArtifactLines('1', '3', 0, signal())).rejects.toThrow('起始行');
  await expect(getArtifactLines('1', '4', 120, signal())).rejects.toThrow('定位');
});
