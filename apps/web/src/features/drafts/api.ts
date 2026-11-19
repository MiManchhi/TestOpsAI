import { readContract, type DraftCommit, type DraftPatch } from '../../app/contracts';
import { inProject, jsonBody, projectPath } from '../../app/api-contract';
import { requestJSON } from '../../app/http';
const path = (projectId: string, draftId: string) =>
  `${projectPath(projectId)}/drafts/${readContract('Uuid', draftId)}`;
export async function getDraft(projectId: string, id: string, signal: AbortSignal) {
  const value = inProject(
    readContract('Draft', await requestJSON(path(projectId, id), signal)),
    projectId,
  );
  if (value.id !== id) throw new Error('草稿响应不匹配。');
  return value;
}
export async function editDraft(
  projectId: string,
  id: string,
  body: DraftPatch,
  signal: AbortSignal,
) {
  readContract('DraftPatch', body);
  const value = inProject(
    readContract('Draft', await requestJSON(path(projectId, id), signal, jsonBody('PATCH', body))),
    projectId,
  );
  if (value.id !== id || value.revision <= body.revision) throw new Error('草稿版本响应不匹配。');
  return value;
}
export async function commitDraft(
  projectId: string,
  id: string,
  body: DraftCommit,
  key: string,
  signal: AbortSignal,
) {
  readContract('DraftCommit', body);
  const value = readContract(
    'DraftCommitResult',
    await requestJSON(path(projectId, id) + '/commit', signal, jsonBody('POST', body, key)),
  );
  // An already committed draft returns its original IDs even if a later caller selected differently.
  if (
    value.draftId !== id ||
    !value.createdCaseIds.length ||
    new Set(value.createdCaseIds).size !== value.createdCaseIds.length
  )
    throw new Error('保存结果不完整，请重新读取草稿确认。');
  return value;
}
export async function discardDraft(projectId: string, id: string, signal: AbortSignal) {
  await requestJSON(path(projectId, id), signal, { method: 'DELETE' });
}
