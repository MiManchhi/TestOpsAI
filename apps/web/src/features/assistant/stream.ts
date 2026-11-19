import { readContract, type TurnEvent } from '../../app/contracts';
import { requestResponse, ApiError } from '../../app/http';
import { auth } from '../auth/session';

/** SSE is framed across arbitrary UTF-8 chunks; metadata and JSON must agree. */
export function createTurnDecoder(
  turnId: string,
  after: number,
  receive: (event: TurnEvent) => void,
) {
  let buffer = '';
  let sequence = after;
  let terminal = false;
  let started = after > 0;
  function frame(value: string) {
    if (value.length > 2_000_000) throw new Error('回答事件超过允许大小。');
    let id = '',
      event = '';
    const data: string[] = [];
    for (const line of value.split(/\r\n|\n|\r/)) {
      if (line.startsWith(':') || !line) continue;
      const separator = line.indexOf(':');
      const field = separator < 0 ? line : line.slice(0, separator);
      const text = separator < 0 ? '' : line.slice(separator + 1).replace(/^ /, '');
      if (field === 'id') id = text;
      else if (field === 'event') event = text;
      else if (field === 'data') data.push(text);
    }
    if (!data.length) return;
    const parsed = readContract('TurnEvent', JSON.parse(data.join('\n')));
    if (parsed.turnId !== turnId || event !== parsed.event || id !== String(parsed.sequence))
      throw new Error('回答事件标识不一致，请重新连接。');
    if (parsed.sequence <= sequence) return;
    if (
      terminal ||
      parsed.sequence !== sequence + 1 ||
      (!started && parsed.event !== 'turn.started') ||
      (started && parsed.event === 'turn.started')
    )
      throw new Error('回答事件顺序不完整，请重新连接。');
    if (parsed.event === 'turn.started') {
      if (parsed.data.id !== turnId) throw new Error('回答轮次不匹配。');
      started = true;
    }
    sequence = parsed.sequence;
    terminal = ['turn.completed', 'turn.failed', 'turn.cancelled'].includes(parsed.event);
    receive(parsed);
  }
  function push(text: string) {
    buffer += text;
    let match: RegExpExecArray | null;
    while ((match = /\r\n\r\n|\n\n|\r\r/.exec(buffer))) {
      const value = buffer.slice(0, match.index);
      buffer = buffer.slice(match.index + match[0].length);
      frame(value);
    }
    if (buffer.length > 2_000_000) throw new Error('回答事件超过允许大小。');
  }
  return {
    push,
    get sequence() {
      return sequence;
    },
    get terminal() {
      return terminal;
    },
  };
}

export async function streamTurn(
  turnId: string,
  after: number,
  signal: AbortSignal,
  receive: (event: TurnEvent) => void,
) {
  const id = readContract('Uuid', turnId);
  const token = auth.getAccessToken();
  const response = await requestResponse(
    `/api/v2/turns/${id}/events`,
    signal,
    { headers: { Accept: 'text/event-stream', 'Last-Event-ID': String(after) } },
    125_000,
  );
  if (
    !response.headers.get('content-type')?.toLowerCase().startsWith('text/event-stream') ||
    !response.body
  )
    throw new ApiError('服务未返回有效回答事件流。');
  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8', { fatal: true });
  const frames = createTurnDecoder(id, after, receive);
  try {
    while (true) {
      const chunk = await reader.read();
      if (signal.aborted || auth.getAccessToken() !== token)
        throw new DOMException('Session changed', 'AbortError');
      if (chunk.done) {
        frames.push(decoder.decode());
        break;
      }
      frames.push(decoder.decode(chunk.value, { stream: true }));
      if (frames.terminal) return;
    }
    if (!frames.terminal) throw new ApiError('回答连接已中断，可重新连接继续接收。');
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}
