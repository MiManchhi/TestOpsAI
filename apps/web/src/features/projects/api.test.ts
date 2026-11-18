import { afterEach, expect, it, vi } from 'vitest';
import { requestJSON } from '../../app/http';
import { listProjects } from './api';
import { sampleProject } from '../test-cases/test-data';

vi.mock('../../app/http', () => ({ requestJSON: vi.fn() }));
afterEach(() => vi.resetAllMocks());

it('requests and reads the frozen ProjectPage pagination contract', async () => {
  const response = { items: [sampleProject], total: 1, page: 0, size: 100 };
  vi.mocked(requestJSON).mockResolvedValue(response);
  const signal = new AbortController().signal;
  expect(await listProjects(0, signal)).toEqual(response);
  expect(requestJSON).toHaveBeenCalledWith('/api/v2/projects?page=0&size=100', signal);
});

it('rejects a bare project array instead of silently adapting an incompatible provider', async () => {
  vi.mocked(requestJSON).mockResolvedValue([sampleProject]);
  await expect(listProjects(0, new AbortController().signal)).rejects.toThrow('约定');
});
