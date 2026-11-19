import { readContract, type TestContext } from '../../app/contracts';
import { projectPath, inProject, pageScope } from '../../app/api-contract';
import { requestJSON } from '../../app/http';

export async function listImports(projectId: string, page: number, signal: AbortSignal) {
  const value = pageScope(
    readContract(
      'ImportJobPage',
      await requestJSON(`${projectPath(projectId)}/imports?page=${page}&size=20`, signal),
    ),
    page,
    20,
  );
  value.items.forEach((item) => inProject(item, projectId));
  return value;
}
export async function getImport(projectId: string, jobId: string, signal: AbortSignal) {
  if (!/^[a-f0-9-]{36}$/i.test(jobId)) throw new Error('导入任务编号无效。');
  const value = inProject(
    readContract(
      'ImportJob',
      await requestJSON(`${projectPath(projectId)}/imports/${jobId}`, signal),
    ),
    projectId,
  );
  if (value.id !== jobId) throw new Error('导入任务响应不匹配。');
  return value;
}
export type CatalogFile = { file: File; logicalPath: string };
export function validateCatalog(files: readonly CatalogFile[]) {
  if (!files.length || files.length > 200)
    throw new Error('请选择 1–200 个 .robot 或 .resource 文件。');
  const paths = new Set<string>();
  let total = 0;
  for (const { file, logicalPath: path } of files) {
    total += file.size;
    if (file.size > 2 * 1024 * 1024 || !/\.(robot|resource)$/i.test(file.name))
      throw new Error('仅接受单个不超过 2 MiB 的 .robot/.resource 文件。');
    if (
      !path ||
      path.length > 512 ||
      path.startsWith('/') ||
      path.includes('\\') ||
      path.split('/').some((part) => !part || part === '.' || part === '..') ||
      /^[a-z]:/i.test(path) ||
      !/\.(robot|resource)$/i.test(path)
    )
      throw new Error('逻辑路径须为相对路径，不能包含空段、反斜杠或 ..。');
    if (paths.has(path)) throw new Error('逻辑路径不能重复。');
    paths.add(path);
  }
  if (total > 20 * 1024 * 1024) throw new Error('目录总大小不能超过 20 MiB。');
}
export async function importCatalog(
  projectId: string,
  files: readonly CatalogFile[],
  key: string,
  signal: AbortSignal,
) {
  validateCatalog(files);
  const body = new FormData();
  files.forEach(({ file, logicalPath }) => {
    body.append('files', file);
    body.append('logicalPaths', logicalPath);
  });
  body.append('mode', 'FULL_SNAPSHOT');
  return readContract(
    'JobAccepted',
    await requestJSON(
      `${projectPath(projectId)}/catalog-imports`,
      signal,
      { method: 'POST', body, headers: { 'Idempotency-Key': key } },
      120_000,
    ),
  );
}
export async function importRun(
  projectId: string,
  file: File,
  name: string,
  revision: string,
  context: TestContext,
  key: string,
  signal: AbortSignal,
) {
  if (!/^output\.xml$/i.test(file.name) || file.size > 50 * 1024 * 1024 || !file.size)
    throw new Error('请选择非空 output.xml，最大 50 MiB。');
  if (!name.trim() || name.length > 256 || !/^[a-f0-9]{64}$/.test(revision))
    throw new Error('填写运行名称和 64 位目录版本摘要。');
  readContract('TestContext', context);
  const body = new FormData();
  body.append('file', file);
  body.append('name', name);
  body.append('catalogRevision', revision);
  body.append('testContext', new Blob([JSON.stringify(context)], { type: 'application/json' }));
  return readContract(
    'JobAccepted',
    await requestJSON(
      `${projectPath(projectId)}/run-imports`,
      signal,
      { method: 'POST', body, headers: { 'Idempotency-Key': key } },
      120_000,
    ),
  );
}
