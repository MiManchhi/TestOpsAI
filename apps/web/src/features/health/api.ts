export type Health = { status: 'UP' };

export async function getServerHealth(): Promise<Health> {
  const response = await fetch('/actuator/health', { signal: AbortSignal.timeout(5000) });
  if (!response.ok) throw new Error(`Health check failed: ${response.status}`);
  const body: unknown = await response.json();
  if (typeof body !== 'object' || body === null || !('status' in body) || body.status !== 'UP') {
    throw new Error('Unexpected health response');
  }
  return { status: 'UP' };
}
