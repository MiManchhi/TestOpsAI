import { jsonBody, resourceId } from '../../app/api-contract';
import { requestJSON, requestResponse } from '../../app/http';
import { readWorkspaceContract } from '../../app/workspace-contracts';

const base = (project: string) => '/api/v3/projects/' + resourceId(project) + '/runs';
const path = (project: string, run: string) => base(project) + '/' + resourceId(run);
const page = (value: number) => new URLSearchParams({ page: String(value), size: '20' });
export async function list(project: string, index: number, q: string, signal: AbortSignal) {
  const query = page(index);
  if (q.trim()) query.set('q', q.trim());
  return readWorkspaceContract('RunPage', await requestJSON(base(project) + '?' + query, signal));
}
export async function register(
  project: string,
  body: {
    name: string;
    executedAt: string;
    outputAttachmentId: string;
    logAttachmentIds: string[];
  },
  key: string,
  signal: AbortSignal,
) {
  return readWorkspaceContract(
    'Run',
    await requestJSON(base(project), signal, jsonBody('POST', body, key)),
  );
}
export async function detail(project: string, id: string, signal: AbortSignal) {
  return readWorkspaceContract('Run', await requestJSON(path(project, id), signal));
}
export async function results(project: string, id: string, index: number, signal: AbortSignal) {
  return readWorkspaceContract(
    'ResultPage',
    await requestJSON(path(project, id) + '/results?' + page(index), signal),
  );
}
export async function artifacts(project: string, id: string, signal: AbortSignal) {
  return readWorkspaceContract(
    'ArtifactPage',
    await requestJSON(path(project, id) + '/artifacts', signal),
  );
}
const artifact = (project: string, id: string, aid: string) =>
  path(project, id) + '/artifacts/' + resourceId(aid);
export async function lines(
  project: string,
  id: string,
  aid: string,
  index: number,
  signal: AbortSignal,
  fromLine = 1,
) {
  const query = page(index);
  query.set('fromLine', String(fromLine));
  return readWorkspaceContract(
    'LogLinePage',
    await requestJSON(artifact(project, id, aid) + '/lines?' + query, signal),
  );
}
export async function download(project: string, id: string, aid: string, signal: AbortSignal) {
  return (
    await requestResponse(artifact(project, id, aid) + '/content', signal, {}, 120000)
  ).blob();
}
