import { jsonBody, resourceId } from '../../app/api-contract';
import { requestJSON, requestResponse } from '../../app/http';
import { readWorkspaceContract, type ChatEvent } from '../../app/workspace-contracts';

const base = (project: string) => '/api/v3/projects/' + resourceId(project);
const pageQuery = (page: number) => new URLSearchParams({ page: String(page), size: '20' });

export async function history(project: string, page: number, query: string, signal: AbortSignal) {
  const params = pageQuery(page);
  if (query.trim()) params.set('q', query.trim());
  return readWorkspaceContract(
    'ConversationPage',
    await requestJSON(base(project) + '/conversations?' + params, signal),
  );
}
export async function messages(
  project: string,
  conversation: string,
  page: number,
  signal: AbortSignal,
) {
  return readWorkspaceContract(
    'MessagePage',
    await requestJSON(
      base(project) +
        '/conversations/' +
        encodeURIComponent(conversation) +
        '/messages?' +
        pageQuery(page),
      signal,
    ),
  );
}
export async function start(
  project: string,
  body: {
    text: string;
    conversationId?: string;
    clientMessageId: string;
    attachmentIds: string[];
    runId?: string;
  },
  key: string,
  signal: AbortSignal,
) {
  return readWorkspaceContract(
    'Turn',
    await requestJSON(base(project) + '/turns', signal, jsonBody('POST', body, key)),
  );
}
export async function turn(project: string, id: string, signal: AbortSignal) {
  return readWorkspaceContract(
    'Turn',
    await requestJSON(base(project) + '/turns/' + encodeURIComponent(id), signal),
  );
}
export async function cancel(project: string, id: string, signal: AbortSignal) {
  return readWorkspaceContract(
    'Turn',
    await requestJSON(base(project) + '/turns/' + encodeURIComponent(id) + '/cancel', signal, {
      method: 'POST',
      headers: { 'Idempotency-Key': crypto.randomUUID() },
    }),
  );
}
export async function citation(project: string, id: string, signal: AbortSignal) {
  return readWorkspaceContract(
    'Citation',
    await requestJSON(base(project) + '/citations/' + encodeURIComponent(id), signal),
  );
}

export async function watchTurn(
  project: string,
  turnId: string,
  after: number,
  signal: AbortSignal,
  receive: (event: ChatEvent) => void,
) {
  const response = await requestResponse(
    base(project) + '/turns/' + encodeURIComponent(turnId) + '/events',
    signal,
    { headers: { Accept: 'text/event-stream', 'Last-Event-ID': String(after) } },
    125000,
  );
  if (!response.headers.get('content-type')?.startsWith('text/event-stream') || !response.body)
    throw new Error('无法读取回答事件。');
  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = '';
  let cursor = after;
  try {
    while (!signal.aborted) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += value;
      const frames = buffer.split(/\r?\n\r?\n/);
      buffer = frames.pop() ?? '';
      if (buffer.length > 512000) throw new Error('回答事件超过允许大小。');
      for (const frame of frames) {
        const rows = frame.split(/\r?\n/);
        const data = rows
          .filter((row) => row.startsWith('data:'))
          .map((row) => row.slice(5).trimStart())
          .join('\n');
        if (!data) continue;
        const event = readWorkspaceContract('Event', JSON.parse(data));
        if (event.sequence <= cursor) continue;
        if (
          event.turnId !== turnId ||
          event.sequence !== cursor + 1 ||
          rows
            .find((row) => row.startsWith('id:'))
            ?.slice(3)
            .trim() !== String(event.sequence) ||
          rows
            .find((row) => row.startsWith('event:'))
            ?.slice(6)
            .trim() !== event.event
        )
          throw new Error('回答事件顺序出现中断。');
        cursor = event.sequence;
        if (!signal.aborted) receive(event);
        if (['turn.completed', 'turn.failed', 'turn.cancelled'].includes(event.event)) return true;
      }
    }
    return false;
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}

type Availability = 'READY' | 'UNCONFIGURED' | 'UNAVAILABLE';
export type AssistantStatus = { chat: Availability; embedding: Availability };
function availability(value: unknown): value is Availability {
  return value === 'READY' || value === 'UNCONFIGURED' || value === 'UNAVAILABLE';
}
export async function assistantStatus(
  project: string,
  signal: AbortSignal,
): Promise<AssistantStatus> {
  const value = await requestJSON(base(project) + '/assistant-status', signal);
  if (
    !value ||
    typeof value !== 'object' ||
    !('chat' in value) ||
    !('embedding' in value) ||
    !availability(value.chat) ||
    !availability(value.embedding)
  )
    throw new Error('无法确认项目模型配置。');
  return { chat: value.chat, embedding: value.embedding };
}
