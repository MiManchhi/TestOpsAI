import { auth } from '../features/auth/session';

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status = 0,
    public readonly code = '',
    public readonly requestId = '',
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '暂时无法读取数据，请重试。';
}

export function requestId(error: unknown): string {
  return error instanceof ApiError ? error.requestId : '';
}

export async function requestResponse(
  path: string,
  signal: AbortSignal,
  options: RequestInit = {},
  timeoutMs = 10_000,
): Promise<Response> {
  const token = auth.getAccessToken();
  if (!token) throw new ApiError('登录已过期，请重新登录。', 401);
  let response: Response;
  try {
    response = await fetch(path, {
      ...options,
      headers: { ...options.headers, Authorization: `Bearer ${token}` },
      signal: AbortSignal.any([signal, AbortSignal.timeout(timeoutMs)]),
    });
  } catch {
    if (signal.aborted) throw new DOMException('Request cancelled', 'AbortError');
    throw new ApiError('暂时无法连接服务，请检查网络后重试。');
  }
  if (signal.aborted || auth.getAccessToken() !== token)
    throw new DOMException('Session changed', 'AbortError');
  if (!response.ok) {
    let body: unknown;
    try {
      body = await response.json();
    } catch {
      body = null;
    }
    if (signal.aborted || auth.getAccessToken() !== token)
      throw new DOMException('Session changed', 'AbortError');
    const record = typeof body === 'object' && body !== null ? body : {};
    const code = 'code' in record && typeof record.code === 'string' ? record.code : '';
    const id =
      'requestId' in record && typeof record.requestId === 'string'
        ? record.requestId
        : (response.headers.get('X-Request-Id') ?? '');
    if (response.status === 401) auth.logout('登录已过期，请重新登录。');
    const message =
      response.status === 401
        ? '登录已过期，请重新登录。'
        : response.status === 403
          ? '你没有访问此项目或内容的权限，请切换项目或联系项目所有者。'
          : response.status === 404
            ? '请求的项目或内容不存在，或已不可访问。'
            : response.status === 409
              ? '数据已更新，请重新读取后确认。'
              : response.status === 413
                ? '上传文件超过大小限制，请缩小文件后重试。'
                : response.status === 415
                  ? '不支持此文件类型，请检查文件格式。'
                  : response.status === 400 || response.status === 422
                    ? '请求内容不符合要求，请检查输入或文件后重试。'
                    : response.status === 429
                      ? '请求过于频繁，请稍后重试。'
                      : '服务暂时不可用，请稍后重试。';
    throw new ApiError(message, response.status, code, id);
  }
  return response;
}

export async function requestJSON(
  path: string,
  signal: AbortSignal,
  options: RequestInit = {},
  timeoutMs = 10_000,
): Promise<unknown> {
  const token = auth.getAccessToken();
  const response = await requestResponse(path, signal, options, timeoutMs);
  let body: unknown;
  try {
    body = await response.json();
  } catch {
    body = null;
  }
  if (signal.aborted || auth.getAccessToken() !== token)
    throw new DOMException('Session changed', 'AbortError');
  return body;
}
