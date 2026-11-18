import {
  readContract,
  type CaseFilter,
  type SelectionCreate,
  type SuitePage,
  type TestCasePage,
} from '../../app/contracts';
import { requestJSON } from '../../app/http';

export type { CaseFilter, SelectionCreate, Selection, Suite, TestCase } from '../../app/contracts';
export type CaseSort = 'title,asc' | 'title,desc' | 'updatedAt,asc' | 'updatedAt,desc';

function prefix(projectId: string) {
  return `/api/v2/projects/${readContract('Id', projectId)}`;
}
function scope(items: ReadonlyArray<{ projectId: string }>, projectId: string) {
  if (items.some((item) => item.projectId !== projectId))
    throw new Error('服务返回的项目范围异常，请重新选择项目。');
}
export function filterQuery(filter: CaseFilter): URLSearchParams {
  readContract('CaseFilter', filter);
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(filter)) {
    if (value === undefined) continue;
    if (Array.isArray(value)) value.forEach((tag) => query.append(key, tag));
    else query.set(key, String(value));
  }
  return query;
}
export async function listSuites(
  projectId: string,
  page: number,
  signal: AbortSignal,
): Promise<SuitePage> {
  const result = readContract(
    'SuitePage',
    await requestJSON(`${prefix(projectId)}/test-suites?page=${page}&size=100`, signal),
  );
  scope(result.items, projectId);
  if (result.page !== page || result.size !== 100) throw new Error('套件分页响应异常，请重试。');
  return result;
}
export async function listTestCases(
  projectId: string,
  filter: CaseFilter,
  page: number,
  size: number,
  sort: CaseSort,
  signal: AbortSignal,
): Promise<TestCasePage> {
  const query = filterQuery(filter);
  query.set('page', String(page));
  query.set('size', String(size));
  query.set('sort', sort);
  const result = readContract(
    'TestCasePage',
    await requestJSON(`${prefix(projectId)}/test-cases?${query}`, signal),
  );
  scope(result.items, projectId);
  if (
    result.page !== page ||
    result.size !== size ||
    new Set(result.items.map((item) => item.id)).size !== result.items.length
  )
    throw new Error('用例分页响应异常，请重试。');
  return result;
}
export async function getTestCase(projectId: string, caseId: string, signal: AbortSignal) {
  const result = readContract(
    'TestCase',
    await requestJSON(`${prefix(projectId)}/test-cases/${readContract('Id', caseId)}`, signal),
  );
  scope([result], projectId);
  if (result.id !== caseId) throw new Error('服务返回的用例与请求不一致，请重试。');
  return result;
}
export async function createCaseSelection(
  projectId: string,
  body: SelectionCreate,
  key: string,
  signal: AbortSignal,
) {
  readContract('SelectionCreate', body);
  const result = readContract(
    'Selection',
    await requestJSON(`${prefix(projectId)}/case-selections`, signal, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key },
      body: JSON.stringify(body),
    }),
  );
  scope([result], projectId);
  if (
    result.count !== result.cases.length ||
    new Set(result.cases.map((item) => item.caseId)).size !== result.count ||
    (body.mode === 'EXPLICIT' && result.cases.some((item) => !body.caseIds.includes(item.caseId)))
  )
    throw new Error('选择确认结果不完整，请重新选择。');
  return result;
}
export const catalogApi = { listSuites, listTestCases, getTestCase, createCaseSelection };
