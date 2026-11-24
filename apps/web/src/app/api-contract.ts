export function resourceId(value: string) {
  if (!/^[1-9][0-9]{0,18}$/.test(value)) throw new Error('项目或资源编号无效。');
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
