import { readContract, type Project, type ProjectPage } from '../../app/contracts';
import { requestJSON } from '../../app/http';

export type { Project };
export async function listProjects(page: number, signal: AbortSignal): Promise<ProjectPage> {
  return readContract(
    'ProjectPage',
    await requestJSON(`/api/v2/projects?page=${page}&size=100`, signal),
  );
}
