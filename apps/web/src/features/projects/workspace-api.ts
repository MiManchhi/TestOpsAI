import { jsonBody, resourceId } from '../../app/api-contract';
import { requestJSON } from '../../app/http';
import { readWorkspaceContract } from '../../app/workspace-contracts';

const platform = '/api/v3/platform';
const project = (id: string) => `/api/v3/projects/${resourceId(id)}`;
const platformProject = (id: string) => `${platform}/projects/${resourceId(id)}`;
const query = (page: number, text?: string) =>
  new URLSearchParams({ page: String(page), size: '20', ...(text ? { q: text } : {}) });

export async function listWorkspaceProjects(
  isPlatform: boolean,
  page: number,
  signal: AbortSignal,
) {
  const path = isPlatform ? `${platform}/projects` : '/api/v3/projects';
  return readWorkspaceContract('ProjectPage', await requestJSON(`${path}?${query(page)}`, signal));
}

export async function listPlatformUsers(page: number, q: string, signal: AbortSignal) {
  return readWorkspaceContract(
    'UserPage',
    await requestJSON(`${platform}/users?${query(page, q)}`, signal),
  );
}

export async function createWorkspaceProject(
  body: { name: string; description: string; ownerId: string },
  key: string,
  signal: AbortSignal,
) {
  return readWorkspaceContract(
    'Project',
    await requestJSON(`${platform}/projects`, signal, jsonBody('POST', body, key)),
  );
}

export async function readWorkspaceProject(id: string, isPlatform: boolean, signal: AbortSignal) {
  const item = readWorkspaceContract(
    'Project',
    await requestJSON(isPlatform ? platformProject(id) : project(id), signal),
  );
  if (item.projectId !== id) throw new Error('项目响应范围异常。');
  return item;
}

export async function listWorkspaceMembers(id: string, page: number, signal: AbortSignal) {
  return readWorkspaceContract(
    'MembershipPage',
    await requestJSON(`${project(id)}/members?${query(page)}`, signal),
  );
}

export async function listMemberCandidates(
  id: string,
  page: number,
  q: string,
  signal: AbortSignal,
) {
  return readWorkspaceContract(
    'UserPage',
    await requestJSON(`${project(id)}/member-candidates?${query(page, q)}`, signal),
  );
}

export async function saveWorkspaceMember(
  id: string,
  user: string,
  body: { role: 'TEST' | 'DEV'; revision: number },
  key: string,
  signal: AbortSignal,
) {
  return readWorkspaceContract(
    'Membership',
    await requestJSON(
      `${project(id)}/members/${resourceId(user)}`,
      signal,
      jsonBody('PUT', body, key),
    ),
  );
}

export async function removeWorkspaceMember(
  id: string,
  user: string,
  revision: number,
  key: string,
  signal: AbortSignal,
) {
  await requestJSON(`${project(id)}/members/${resourceId(user)}?revision=${revision}`, signal, {
    method: 'DELETE',
    headers: { 'Idempotency-Key': key },
  });
}

export async function listOwnerTransfers(
  id: string,
  isPlatform: boolean,
  page: number,
  signal: AbortSignal,
) {
  const path = isPlatform ? platformProject(id) : project(id);
  return readWorkspaceContract(
    'HandoverPage',
    await requestJSON(`${path}/owner-transfers?${query(page)}`, signal),
  );
}

export async function editWorkspaceProject(
  id: string,
  body: { name: string; description: string; revision: number },
  key: string,
  signal: AbortSignal,
) {
  return readWorkspaceContract(
    'Project',
    await requestJSON(project(id), signal, jsonBody('PATCH', body, key)),
  );
}

export async function requestOwnerTransfer(
  id: string,
  body: { newOwnerId: string; formerRole: 'TEST' | 'DEV'; revision: number },
  key: string,
  signal: AbortSignal,
) {
  return readWorkspaceContract(
    'Handover',
    await requestJSON(`${project(id)}/owner-transfers`, signal, jsonBody('POST', body, key)),
  );
}

export async function decideOwnerTransfer(
  id: string,
  handoverId: string,
  body: { decision: 'APPROVE' | 'RETURN'; note: string; revision: number },
  key: string,
  signal: AbortSignal,
) {
  return readWorkspaceContract(
    'Handover',
    await requestJSON(
      `${platformProject(id)}/owner-transfers/${encodeURIComponent(handoverId)}/decision`,
      signal,
      jsonBody('POST', body, key),
    ),
  );
}

export async function listEligibleOwners(id: string, page: number, q: string, signal: AbortSignal) {
  return readWorkspaceContract(
    'UserPage',
    await requestJSON(`${platformProject(id)}/successors?${query(page, q)}`, signal),
  );
}

export async function proxyOwnerTransfer(
  id: string,
  body: {
    newOwnerId: string;
    formerRole: 'TEST' | 'DEV';
    revision: number;
    unavailableReason: string;
  },
  key: string,
  signal: AbortSignal,
) {
  return readWorkspaceContract(
    'Handover',
    await requestJSON(
      `${platformProject(id)}/owner-transfers/proxy`,
      signal,
      jsonBody('POST', body, key),
    ),
  );
}
