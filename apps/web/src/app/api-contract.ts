import { readContract } from './contracts';
import { requestJSON } from './http';
export function projectPath(projectId: string) {
  return `/api/v2/projects/${readContract('Id', projectId)}`;
}
export function resourceId(value: string) {
  return readContract('Id', value);
}
export function inProject<T extends { projectId: string }>(value: T, projectId: string): T {
  if (value.projectId !== projectId) throw new Error('服务返回的项目范围异常，请重新读取。');
  return value;
}
export function pageScope<
  T extends { page: number; size: number; total: number; items: readonly unknown[] },
>(value: T, page: number, size: number): T {
  if (
    value.page !== page ||
    value.size !== size ||
    value.items.length > size ||
    (value.items.length === 0 && value.total > page * size)
  )
    throw new Error('分页响应不完整，请重新读取。');
  return value;
}
export function jsonBody(method: string, body: unknown, key?: string): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json', ...(key ? { 'Idempotency-Key': key } : {}) },
    body: JSON.stringify(body),
  };
}
export async function getOverview(projectId: string, signal: AbortSignal) {
  const value = readContract(
    'Overview',
    await requestJSON(`${projectPath(projectId)}/overview`, signal),
  );
  if (value.project.id !== projectId) throw new Error('工作台项目范围异常。');
  value.recentRuns.forEach((run) => inProject(run, projectId));
  if (value.lastRun) inProject(value.lastRun, projectId);
  return value;
}
