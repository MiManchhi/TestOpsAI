import { expect, it, vi } from 'vitest';
import { ApiError } from '../../app/http';
import { createCaseCatalog } from './catalog';
import { casePage, selection } from './test-data';
function fixture() {
  const api = {
    listSuites: vi.fn().mockResolvedValue({ items: [], page: 0, size: 100, total: 0 }),
    listTestCases: vi.fn().mockResolvedValue(casePage()),
    createCaseSelection: vi.fn().mockResolvedValue(selection()),
  };
  const catalog = createCaseCatalog(api);
  catalog.setProject('1');
  return { api, catalog };
}
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((callback) => {
    resolve = callback;
  });
  return { promise, resolve };
}
it('preserves explicit choices across pages and clears them when filters change', async () => {
  const { api, catalog } = fixture();
  await catalog.loadCases();
  catalog.toggleCase('1');
  api.listTestCases.mockResolvedValueOnce(casePage(1, 21, '2'));
  await catalog.loadCases(1);
  catalog.toggleCase('2');
  expect(catalog.state.explicitIds).toEqual(['1', '2']);
  await catalog.applyFilter({ tags: ['smoke'], tagsMatch: 'ALL' });
  expect(catalog.state.explicitIds).toEqual([]);
  expect(catalog.state.page).toBe(0);
});
it('ignores an old project response even if the transport ignores cancellation', async () => {
  const { api, catalog } = fixture();
  const old = deferred<ReturnType<typeof casePage>>();
  api.listTestCases.mockReturnValueOnce(old.promise);
  const loading = catalog.loadCases();
  catalog.toggleCase('1');
  catalog.setProject('2');
  old.resolve(casePage());
  await loading;
  expect(catalog.state.projectId).toBe('2');
  expect(catalog.state.items).toEqual([]);
  expect(catalog.state.explicitIds).toEqual([]);
});
it('ignores a stale query and retains the latest response', async () => {
  const { api, catalog } = fixture();
  const old = deferred<ReturnType<typeof casePage>>();
  api.listTestCases.mockReturnValueOnce(old.promise).mockResolvedValueOnce(casePage(0, 1, '2'));
  const oldLoading = catalog.loadCases();
  await catalog.applyFilter({ query: 'new' });
  old.resolve(casePage());
  await oldLoading;
  expect(catalog.state.items[0]?.id).toBe('2');
});
it('confirms FILTER exclusions with a stable idempotency key after a network failure', async () => {
  const { api, catalog } = fixture();
  await catalog.loadCases();
  catalog.selectAllMatching();
  catalog.toggleCase('2');
  api.createCaseSelection.mockRejectedValueOnce(new ApiError('network'));
  await catalog.confirmSelection();
  await catalog.confirmSelection();
  const first = api.createCaseSelection.mock.calls[0];
  const retry = api.createCaseSelection.mock.calls[1];
  expect(first?.[1]).toEqual({
    mode: 'FILTER',
    filter: { lifecycleStatus: 'ACTIVE' },
    excludedCaseIds: ['2'],
  });
  expect(first?.[2]).toBe(retry?.[2]);
  expect(catalog.state.selection?.count).toBe(1);
  catalog.toggleCase('3');
  await catalog.confirmSelection();
  expect(api.createCaseSelection.mock.calls[2]?.[2]).not.toBe(first?.[2]);
});
it('rejects an old selection confirmation after the project changes', async () => {
  const { api, catalog } = fixture();
  await catalog.loadCases();
  catalog.toggleCase('1');
  const old = deferred<ReturnType<typeof selection>>();
  api.createCaseSelection.mockReturnValueOnce(old.promise);
  const saving = catalog.confirmSelection();
  catalog.setProject('2');
  old.resolve(selection());
  await saving;
  expect(catalog.state.selection).toBeNull();
  expect(catalog.state.saving).toBe(false);
});
it('clears loaded content and choices on revocation without falling back to fixtures', async () => {
  const { api, catalog } = fixture();
  await catalog.loadCases();
  catalog.toggleCase('1');
  api.listTestCases.mockRejectedValueOnce(new ApiError('forbidden', 403, 'FORBIDDEN', 'req-1'));
  await catalog.loadCases();
  expect(catalog.state.phase).toBe('error');
  expect(catalog.state.items).toEqual([]);
  expect(catalog.state.explicitIds).toEqual([]);
  expect(catalog.state.requestId).toBe('req-1');
});
it('does not loop forever on incomplete suites and rejects cyclic suite graphs', async () => {
  const { api, catalog } = fixture();
  api.listSuites.mockResolvedValueOnce({ items: [], page: 0, size: 100, total: 1 });
  await catalog.loadSuites();
  expect(catalog.state.suitePhase).toBe('error');
  const suite = {
    id: '1',
    projectId: '1',
    parentId: '1',
    name: 'cycle',
    sourcePath: 'demo.robot',
    revision: 'a'.repeat(64),
    caseCount: 1,
  };
  api.listSuites.mockResolvedValueOnce({ items: [suite], page: 0, size: 100, total: 1 });
  await catalog.loadSuites();
  expect(catalog.state.suiteError).toContain('层级');
  expect(catalog.state.suites).toEqual([]);
});
