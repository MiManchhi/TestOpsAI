import { readContract, type MessageCreate, type ConversationCreate } from '../../app/contracts';
import { pageScope, resourceId, inProject, jsonBody } from '../../app/api-contract';
import { requestJSON } from '../../app/http';
export const uuid = (id: string) => readContract('Uuid', id);
export async function listConversations(
  projectId: string,
  page: number,
  query: string,
  signal: AbortSignal,
) {
  const params = new URLSearchParams({
    projectId: resourceId(projectId),
    page: String(page),
    size: '20',
    ...(query ? { query } : {}),
  });
  const value = pageScope(
    readContract('ConversationPage', await requestJSON(`/api/v2/conversations?${params}`, signal)),
    page,
    20,
  );
  value.items.forEach((item) => inProject(item, projectId));
  return value;
}
export async function createConversation(
  body: ConversationCreate,
  key: string,
  signal: AbortSignal,
) {
  readContract('ConversationCreate', body);
  return inProject(
    readContract(
      'Conversation',
      await requestJSON('/api/v2/conversations', signal, jsonBody('POST', body, key)),
    ),
    body.projectId,
  );
}
export async function getConversation(projectId: string, id: string, signal: AbortSignal) {
  const value = inProject(
    readContract('Conversation', await requestJSON(`/api/v2/conversations/${uuid(id)}`, signal)),
    projectId,
  );
  if (value.id !== id) throw new Error('会话响应不匹配。');
  return value;
}
export async function deleteConversation(id: string, signal: AbortSignal) {
  await requestJSON(`/api/v2/conversations/${uuid(id)}`, signal, { method: 'DELETE' });
}
export async function listMessages(conversationId: string, page: number, signal: AbortSignal) {
  const value = pageScope(
    readContract(
      'MessagePage',
      await requestJSON(
        `/api/v2/conversations/${uuid(conversationId)}/messages?page=${page}&size=20`,
        signal,
      ),
    ),
    page,
    20,
  );
  if (value.items.some((item) => item.conversationId !== conversationId))
    throw new Error('消息范围异常。');
  return value;
}
function turnScope(
  value: ReturnType<typeof readContract<'Turn'>>,
  conversationId: string,
  turnId?: string,
) {
  if (value.conversationId !== conversationId || (turnId && value.id !== turnId))
    throw new Error('回答轮次响应不匹配。');
  return value;
}
export async function sendMessage(
  conversationId: string,
  body: MessageCreate,
  key: string,
  signal: AbortSignal,
) {
  readContract('MessageCreate', body);
  return turnScope(
    readContract(
      'Turn',
      await requestJSON(
        `/api/v2/conversations/${uuid(conversationId)}/messages`,
        signal,
        jsonBody('POST', body, key),
      ),
    ),
    conversationId,
  );
}
export async function getTurn(conversationId: string, turnId: string, signal: AbortSignal) {
  return turnScope(
    readContract('Turn', await requestJSON(`/api/v2/turns/${uuid(turnId)}`, signal)),
    conversationId,
    turnId,
  );
}
export async function cancelTurn(
  conversationId: string,
  turnId: string,
  key: string,
  signal: AbortSignal,
) {
  const value = turnScope(
    readContract(
      'Turn',
      await requestJSON(`/api/v2/turns/${uuid(turnId)}/cancel`, signal, {
        method: 'POST',
        headers: { 'Idempotency-Key': key },
      }),
    ),
    conversationId,
    turnId,
  );
  if (value.status === 'QUEUED' || value.status === 'RUNNING')
    throw new Error('停止请求未返回终态，请读取本轮状态。');
  return value;
}
