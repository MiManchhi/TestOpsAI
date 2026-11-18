import { expect, it, vi } from 'vitest';
import { createProjectContext } from './context';
import { sampleProject } from '../test-cases/test-data';
import type { ProjectPage } from '../../app/contracts';
it('requires an explicit project choice when a deep link is no longer authorized', async () => {
  const context = createProjectContext(
    vi.fn().mockResolvedValue({ items: [sampleProject], total: 1, page: 0, size: 100 }),
  );
  await context.load('999');
  expect(context.state.selectedId).toBe('');
  expect(context.current.value).toBeNull();
  expect(context.state.error).toContain('不可访问');
  context.select('1');
  expect(context.current.value?.id).toBe('1');
  await context.load(['1', '2']);
  expect(context.state.selectedId).toBe('');
});
it('loads all project pages and validates a preferred project before selecting it', async () => {
  const first = Array.from({ length: 100 }, (_, i) => ({ ...sampleProject, id: String(i + 1) }));
  const loadPage = vi
    .fn()
    .mockResolvedValueOnce({ items: first, total: 101, page: 0, size: 100 })
    .mockResolvedValueOnce({
      items: [{ ...sampleProject, id: '101' }],
      total: 101,
      page: 1,
      size: 100,
    });
  const context = createProjectContext(loadPage);
  await context.load('101');
  expect(context.state.items).toHaveLength(101);
  expect(context.current.value?.id).toBe('101');
});
it('clears previous project content on refresh failure and supports empty authorized lists', async () => {
  const loadPage = vi
    .fn()
    .mockResolvedValueOnce({ items: [sampleProject], total: 1, page: 0, size: 100 })
    .mockRejectedValueOnce(new Error('unavailable'))
    .mockResolvedValueOnce({ items: [], total: 0, page: 0, size: 100 });
  const context = createProjectContext(loadPage);
  await context.load();
  await context.load();
  expect(context.state.phase).toBe('error');
  expect(context.current.value).toBeNull();
  expect(context.state.items).toEqual([]);
  await context.load();
  expect(context.state.phase).toBe('ready');
  expect(context.state.selectedId).toBe('');
});
it('does not restore a stale project list after logout/reset', async () => {
  let resolve!: (value: ProjectPage) => void;
  const loadPage = vi.fn().mockImplementation(
    () =>
      new Promise<ProjectPage>((callback) => {
        resolve = callback;
      }),
  );
  const context = createProjectContext(loadPage);
  const loading = context.load();
  context.reset();
  resolve({ items: [sampleProject], total: 1, page: 0, size: 100 });
  await loading;
  expect(context.state.items).toEqual([]);
  expect(context.state.phase).toBe('idle');
});
