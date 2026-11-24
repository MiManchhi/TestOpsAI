import type { LocationQuery } from 'vue-router';
import type { WorkspaceAttachment } from '../../app/workspace-contracts';

export function handoffId(value: unknown) {
  return typeof value === 'string' && /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(value)
    ? value
    : '';
}
export function handoffFiles(values: LocationQuery[string] | undefined) {
  return (Array.isArray(values) ? values : [values]).map(handoffId).filter(Boolean).slice(0, 10);
}
export function returnToChat(project: string, query: LocationQuery) {
  const linked = handoffId(query.returnConversationId);
  return linked ? '/p/' + project + '/chat/' + linked : '';
}
export function carryFromChat(conversationId: string, attachments: WorkspaceAttachment[]) {
  const live = attachments.filter((file) => Date.parse(file.expiresAt) > Date.now());
  const output = live.find((file) => /^output\.xml$/i.test(file.fileName))?.attachmentId;
  const logs = live
    .filter((file) => /\.(txt|log)$/i.test(file.fileName))
    .map((file) => file.attachmentId);
  const document = live.find((file) =>
    /\.(pdf|docx|md|markdown)$/i.test(file.fileName),
  )?.attachmentId;
  return {
    run: {
      returnConversationId: conversationId,
      ...(output ? { output } : {}),
      ...(logs.length ? { logs } : {}),
    },
    document: {
      returnConversationId: conversationId,
      ...(document ? { attachmentId: document } : {}),
    },
  };
}
