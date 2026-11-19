import { afterEach, expect, it, vi } from 'vitest';
import { requestJSON } from '../../app/http';
import { getImport, importCatalog, importRun, listImports, validateCatalog } from './api';
vi.mock('../../app/http', () => ({ requestJSON: vi.fn() }));
afterEach(() => vi.resetAllMocks());
const signal = () => new AbortController().signal;
const jobId = '00000000-0000-4000-8000-000000000001';
const file = () => new File(['*** Test Cases ***\nMeter\n    No Operation'], 'meter.robot');
it('sends parallel multipart files/logicalPaths and the same retry key', async () => {
  vi.mocked(requestJSON).mockResolvedValue({ jobId, status: 'QUEUED' });
  const files = [{ file: file(), logicalPath: 'suites/meter.robot' }];
  await importCatalog('1', files, jobId, signal());
  await importCatalog('1', files, jobId, signal());
  const options = vi.mocked(requestJSON).mock.calls[0]?.[2];
  expect(options?.headers).toEqual({ 'Idempotency-Key': jobId });
  expect(options?.body).toBeInstanceOf(FormData);
  const body = options?.body as FormData;
  expect(body.getAll('files')).toHaveLength(1);
  expect(body.getAll('logicalPaths')).toEqual(['suites/meter.robot']);
  expect(body.get('mode')).toBe('FULL_SNAPSHOT');
  expect(vi.mocked(requestJSON).mock.calls[1]?.[2]?.headers).toEqual(options?.headers);
});
it('rejects unsafe paths, duplicate paths, wrong types and oversized snapshots before HTTP', async () => {
  for (const path of [
    '../meter.robot',
    '/meter.robot',
    'C:/meter.robot',
    'a\\meter.robot',
    'a//meter.robot',
    'a/./meter.robot',
  ])
    expect(() => validateCatalog([{ file: file(), logicalPath: path }])).toThrow();
  expect(() =>
    validateCatalog([
      { file: file(), logicalPath: 'same.robot' },
      { file: file(), logicalPath: 'same.robot' },
    ]),
  ).toThrow('重复');
  await expect(
    importCatalog(
      '1',
      [{ file: new File(['x'], 'wrong.zip'), logicalPath: 'x.robot' }],
      jobId,
      signal(),
    ),
  ).rejects.toThrow();
  expect(() =>
    validateCatalog([
      {
        file: new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'big.robot'),
        logicalPath: 'big.robot',
      },
    ]),
  ).toThrow('2 MiB');
  expect(requestJSON).not.toHaveBeenCalled();
});
it('uploads testContext as application/json and preserves the selected catalog revision', async () => {
  vi.mocked(requestJSON).mockResolvedValue({ jobId, status: 'QUEUED' });
  await importRun(
    '1',
    new File(['<robot/>'], 'output.xml'),
    'Meter run',
    'a'.repeat(64),
    { deviceType: 'SINGLE_PHASE', firmwareVersion: 'demo' },
    jobId,
    signal(),
  );
  const body = vi.mocked(requestJSON).mock.calls[0]?.[2]?.body as FormData;
  const context = body.get('testContext');
  expect(context).toBeInstanceOf(Blob);
  if (!(context instanceof Blob)) throw new Error('Missing multipart context');
  expect(context.type).toBe('application/json');
  expect(JSON.parse(await context.text())).toEqual({
    deviceType: 'SINGLE_PHASE',
    firmwareVersion: 'demo',
  });
  expect(body.get('catalogRevision')).toBe('a'.repeat(64));
});
it('rejects an invalid result file and cross-project history without fallback', async () => {
  await expect(
    importRun(
      '1',
      new File(['x'], 'log.html'),
      'r',
      'a'.repeat(64),
      { deviceType: 'UNSPECIFIED' },
      jobId,
      signal(),
    ),
  ).rejects.toThrow('output.xml');
  vi.mocked(requestJSON).mockResolvedValue({ items: [], page: 1, size: 20, total: 0 });
  await expect(listImports('1', 0, signal())).rejects.toThrow('分页');
  vi.mocked(requestJSON).mockResolvedValue({
    id: jobId,
    projectId: '2',
    kind: 'ROBOT_RESULT',
    status: 'FAILED',
    createdAt: new Date().toISOString(),
    completedAt: null,
    counts: { added: 0, updated: 0, archived: 0, unmapped: 0 },
    issues: [],
    runId: null,
    revision: null,
  });
  await expect(getImport('1', jobId, signal())).rejects.toThrow('项目范围');
});
