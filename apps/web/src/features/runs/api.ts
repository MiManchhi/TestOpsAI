import { readContract } from '../../app/contracts';
import { projectPath, resourceId, inProject, pageScope } from '../../app/api-contract';
import { requestJSON } from '../../app/http';
export type RunStatus = '' | 'COMPLETED' | 'INCOMPLETE';
export type ResultStatus = '' | 'PASS' | 'FAIL' | 'SKIP';
export async function listRuns(
  projectId: string,
  page: number,
  status: RunStatus,
  signal: AbortSignal,
) {
  const value = pageScope(
    readContract(
      'RunPage',
      await requestJSON(
        `${projectPath(projectId)}/runs?page=${page}&size=20${status ? '&status=' + status : ''}`,
        signal,
      ),
    ),
    page,
    20,
  );
  value.items.forEach((run) => inProject(run, projectId));
  return value;
}
export async function getRun(projectId: string, runId: string, signal: AbortSignal) {
  const value = inProject(
    readContract(
      'Run',
      await requestJSON(`${projectPath(projectId)}/runs/${resourceId(runId)}`, signal),
    ),
    projectId,
  );
  if (
    value.id !== runId ||
    value.counts.total !== value.counts.pass + value.counts.fail + value.counts.skip
  )
    throw new Error('运行响应或结果统计不一致。');
  return value;
}
export async function listResults(
  projectId: string,
  runId: string,
  page: number,
  status: ResultStatus,
  signal: AbortSignal,
) {
  const value = pageScope(
    readContract(
      'ResultPage',
      await requestJSON(
        `${projectPath(projectId)}/runs/${resourceId(runId)}/results?page=${page}&size=20${status ? '&status=' + status : ''}`,
        signal,
      ),
    ),
    page,
    20,
  );
  if (value.items.some((result) => result.runId !== runId || (status && result.status !== status)))
    throw new Error('运行结果范围异常。');
  return value;
}
export async function listArtifacts(
  projectId: string,
  runId: string,
  page: number,
  signal: AbortSignal,
) {
  const value = pageScope(
    readContract(
      'ArtifactPage',
      await requestJSON(
        `${projectPath(projectId)}/runs/${resourceId(runId)}/artifacts?page=${page}&size=20`,
        signal,
      ),
    ),
    page,
    20,
  );
  if (value.items.some((item) => item.runId !== runId)) throw new Error('运行产物范围异常。');
  return value;
}
export async function getArtifactLines(
  projectId: string,
  artifactId: string,
  fromLine: number,
  signal: AbortSignal,
) {
  if (!Number.isSafeInteger(fromLine) || fromLine < 1) throw new Error('日志起始行无效。');
  const value = readContract(
    'ArtifactLines',
    await requestJSON(
      `${projectPath(projectId)}/artifacts/${resourceId(artifactId)}/lines?fromLine=${fromLine}&limit=100`,
      signal,
    ),
  );
  if (value.artifactId !== artifactId || value.fromLine !== fromLine)
    throw new Error('日志定位响应不一致。');
  return value;
}
