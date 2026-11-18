import { readContract, type Project, type ProjectPage } from '../../app/contracts';
import { requestJSON } from '../../app/http';
import { jsonBody, pageScope, projectPath, resourceId } from '../../app/api-contract';
import type { ProjectCreate, ProjectPatch, SetMember } from '../../app/contracts';

export type { Project };
export async function getProject(projectId: string, signal: AbortSignal) {
  const value = readContract('Project', await requestJSON(projectPath(projectId), signal));
  if (value.id !== projectId) throw new Error('项目响应范围异常。');
  return value;
}
export async function createProject(body: ProjectCreate, key: string, signal: AbortSignal) {
  readContract('ProjectCreate', body);
  return readContract(
    'Project',
    await requestJSON('/api/v2/projects', signal, jsonBody('POST', body, key)),
  );
}
export async function updateProject(projectId: string, body: ProjectPatch, signal: AbortSignal) {
  readContract('ProjectPatch', body);
  const value = readContract(
    'Project',
    await requestJSON(projectPath(projectId), signal, jsonBody('PATCH', body)),
  );
  if (value.id !== projectId) throw new Error('项目响应范围异常。');
  return value;
}
export async function listMembers(projectId: string, page: number, signal: AbortSignal) {
  return pageScope(
    readContract(
      'MemberPage',
      await requestJSON(`${projectPath(projectId)}/members?page=${page}&size=20`, signal),
    ),
    page,
    20,
  );
}
export async function setMember(
  projectId: string,
  userId: string,
  body: SetMember,
  signal: AbortSignal,
) {
  readContract('SetMember', body);
  const value = readContract(
    'Member',
    await requestJSON(
      `${projectPath(projectId)}/members/${resourceId(userId)}`,
      signal,
      jsonBody('PUT', body),
    ),
  );
  if (value.userId !== userId || value.role !== body.role) throw new Error('成员响应不匹配。');
  return value;
}
export async function removeMember(projectId: string, userId: string, signal: AbortSignal) {
  await requestJSON(`${projectPath(projectId)}/members/${resourceId(userId)}`, signal, {
    method: 'DELETE',
  });
}
export async function listProjects(page: number, signal: AbortSignal): Promise<ProjectPage> {
  return readContract(
    'ProjectPage',
    await requestJSON(`/api/v2/projects?page=${page}&size=100`, signal),
  );
}
