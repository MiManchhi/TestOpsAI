export type LoginResult = { accessToken: string; expiresIn: number };
export type CurrentUser = { id: number; username: string; roles: string[]; createdAt: string };

export class AuthError extends Error {
  constructor(
    message: string,
    public readonly status = 0,
    public readonly requestId = '',
  ) {
    super(message);
    this.name = 'AuthError';
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

async function request(path: string, options: RequestInit, signal: AbortSignal): Promise<unknown> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...options,
      signal: AbortSignal.any([signal, AbortSignal.timeout(10_000)]),
    });
  } catch {
    if (signal.aborted) throw new DOMException('Request cancelled', 'AbortError');
    throw new AuthError('暂时无法连接服务，请检查网络后重试。');
  }
  if (!response.ok) {
    let body: unknown;
    try {
      body = await response.json();
    } catch {
      body = null;
    }
    const requestId =
      isRecord(body) && typeof body.requestId === 'string'
        ? body.requestId
        : (response.headers.get('X-Request-Id') ?? '');
    const message =
      response.status === 401
        ? path.endsWith('/login')
          ? '用户名或密码不正确，请重新输入。'
          : '登录已过期，请重新登录。'
        : response.status === 403
          ? '你暂时没有访问此内容的权限。'
          : '服务暂时不可用，请稍后重试。';
    throw new AuthError(message, response.status, requestId);
  }
  try {
    return await response.json();
  } catch {
    throw new AuthError('服务响应格式异常，请稍后重试。');
  }
}

export async function login(
  username: string,
  password: string,
  signal: AbortSignal,
): Promise<LoginResult> {
  const body = await request(
    '/api/v1/auth/login',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    },
    signal,
  );
  if (
    !isRecord(body) ||
    typeof body.accessToken !== 'string' ||
    !body.accessToken.trim() ||
    typeof body.expiresIn !== 'number' ||
    !Number.isSafeInteger(body.expiresIn) ||
    body.expiresIn <= 0 ||
    body.expiresIn > 86400
  ) {
    throw new AuthError('服务响应格式异常，请稍后重试。');
  }
  return { accessToken: body.accessToken, expiresIn: body.expiresIn };
}

export async function getCurrentUser(token: string, signal: AbortSignal): Promise<CurrentUser> {
  const body = await request(
    '/api/v1/users/me',
    { headers: { Authorization: `Bearer ${token}` } },
    signal,
  );
  if (
    !isRecord(body) ||
    typeof body.id !== 'number' ||
    !Number.isSafeInteger(body.id) ||
    body.id <= 0 ||
    typeof body.username !== 'string' ||
    !body.username.trim() ||
    !Array.isArray(body.roles) ||
    !body.roles.every((role: unknown) => typeof role === 'string') ||
    typeof body.createdAt !== 'string' ||
    !Number.isFinite(Date.parse(body.createdAt))
  ) {
    throw new AuthError('服务响应格式异常，请稍后重试。');
  }
  return { id: body.id, username: body.username, roles: body.roles, createdAt: body.createdAt };
}
