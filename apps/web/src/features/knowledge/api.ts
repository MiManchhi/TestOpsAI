import {
  readContract,
  type KnowledgeBaseCreate,
  type KnowledgeBasePatch,
  type SetKnowledgeGrant,
} from '../../app/contracts';
import { projectPath, resourceId, inProject, pageScope, jsonBody } from '../../app/api-contract';
import { requestJSON } from '../../app/http';
export const categories = {
  METERING: '电能计量',
  PROTOCOL: '通信规约',
  TARIFF_CLOCK: '费率与时钟',
  PREPAYMENT: '费控',
  EVENTS: '事件',
  ROBOT_METHOD: 'Robot 方法',
};
export const documentStatus = {
  QUEUED: '排队中',
  PROCESSING: '处理中',
  READY: '可阅读',
  FAILED: '处理失败',
  DELETING: '清理中',
  DELETED: '已删除',
};
export const documentStage = {
  UPLOADING: '上传',
  PARSING: '解析',
  CHUNKING: '内容分块',
  EMBEDDING: '建立索引',
  FINISHED: '处理完成',
  CLEANUP: '清理内容',
};
export type Category = keyof typeof categories;
const kbPath = (projectId: string, kbId: string) =>
  `${projectPath(projectId)}/knowledge-bases/${resourceId(kbId)}`;
export async function listKnowledgeBases(
  projectId: string,
  page: number,
  query: string,
  category: Category | '',
  signal: AbortSignal,
) {
  const params = new URLSearchParams({ page: String(page), size: '20' });
  if (query) params.set('query', query);
  if (category) params.set('category', category);
  const value = pageScope(
    readContract(
      'KnowledgeBasePage',
      await requestJSON(`${projectPath(projectId)}/knowledge-bases?${params}`, signal),
    ),
    page,
    20,
  );
  value.items.forEach((item) => inProject(item, projectId));
  return value;
}
export async function getKnowledgeBase(projectId: string, kbId: string, signal: AbortSignal) {
  const value = inProject(
    readContract('KnowledgeBase', await requestJSON(kbPath(projectId, kbId), signal)),
    projectId,
  );
  if (value.id !== kbId || value.readyCount > value.documentCount)
    throw new Error('知识库响应不匹配。');
  return value;
}
export async function createKnowledgeBase(
  projectId: string,
  body: KnowledgeBaseCreate,
  key: string,
  signal: AbortSignal,
) {
  readContract('KnowledgeBaseCreate', body);
  return inProject(
    readContract(
      'KnowledgeBase',
      await requestJSON(
        `${projectPath(projectId)}/knowledge-bases`,
        signal,
        jsonBody('POST', body, key),
      ),
    ),
    projectId,
  );
}
export async function updateKnowledgeBase(
  projectId: string,
  kbId: string,
  body: KnowledgeBasePatch,
  signal: AbortSignal,
) {
  readContract('KnowledgeBasePatch', body);
  const value = inProject(
    readContract(
      'KnowledgeBase',
      await requestJSON(kbPath(projectId, kbId), signal, jsonBody('PATCH', body)),
    ),
    projectId,
  );
  if (value.id !== kbId) throw new Error('知识库响应不匹配。');
  return value;
}
export async function listDocuments(
  projectId: string,
  kbId: string,
  page: number,
  signal: AbortSignal,
) {
  const value = pageScope(
    readContract(
      'DocumentPage',
      await requestJSON(`${kbPath(projectId, kbId)}/documents?page=${page}&size=20`, signal),
    ),
    page,
    20,
  );
  if (value.items.some((item) => item.knowledgeBaseId !== kbId)) throw new Error('文档范围异常。');
  return value;
}
export async function getDocument(
  projectId: string,
  documentId: string,
  signal: AbortSignal,
  kbId?: string,
) {
  const value = readContract(
    'Document',
    await requestJSON(`${projectPath(projectId)}/documents/${resourceId(documentId)}`, signal),
  );
  if (value.id !== documentId || (kbId && value.knowledgeBaseId !== kbId))
    throw new Error('文档响应不匹配。');
  return value;
}
export function validateDocumentFile(file: File) {
  const markdown = /\.md$/i.test(file.name);
  if (!file.size || (!markdown && !/\.pdf$/i.test(file.name)))
    throw new Error('请选择非空 Markdown (.md) 或 PDF 文件。');
  if (file.size > (markdown ? 2 : 20) * 1024 * 1024)
    throw new Error(markdown ? 'Markdown 最大 2 MiB。' : 'PDF 最大 20 MiB。');
}
export async function uploadDocument(
  projectId: string,
  kbId: string,
  file: File,
  title: string,
  versionLabel: string,
  key: string,
  signal: AbortSignal,
) {
  validateDocumentFile(file);
  if (!title.trim() || title.length > 256 || !versionLabel.trim() || versionLabel.length > 64)
    throw new Error('填写文档标题和版本标签。');
  const body = new FormData();
  body.append('file', file);
  body.append('title', title);
  body.append('versionLabel', versionLabel);
  const value = readContract(
    'Document',
    await requestJSON(
      `${kbPath(projectId, kbId)}/documents`,
      signal,
      { method: 'POST', body, headers: { 'Idempotency-Key': key } },
      120_000,
    ),
  );
  if (value.knowledgeBaseId !== kbId) throw new Error('文档响应不匹配。');
  return value;
}
export async function changeDocument(
  projectId: string,
  kbId: string,
  documentId: string,
  operation: 'retry' | 'delete',
  key: string,
  signal: AbortSignal,
) {
  const path = `${projectPath(projectId)}/documents/${resourceId(documentId)}`;
  const value = readContract(
    'Document',
    await requestJSON(path + (operation === 'retry' ? '/retry' : ''), signal, {
      method: operation === 'retry' ? 'POST' : 'DELETE',
      headers: { 'Idempotency-Key': key },
    }),
  );
  if (value.id !== documentId || value.knowledgeBaseId !== kbId)
    throw new Error('文档响应不匹配。');
  return value;
}
export async function getDocumentContent(
  projectId: string,
  kbId: string,
  documentId: string,
  cursor: string,
  signal: AbortSignal,
) {
  const value = readContract(
    'DocumentContent',
    await requestJSON(
      `${projectPath(projectId)}/documents/${resourceId(documentId)}/content${cursor ? '?cursor=' + encodeURIComponent(cursor) : ''}`,
      signal,
    ),
  );
  if (
    value.document.id !== documentId ||
    value.document.knowledgeBaseId !== kbId ||
    value.document.status !== 'READY'
  )
    throw new Error('文档版本或可阅读状态不匹配。');
  return value;
}
export async function getCitation(projectId: string, citationId: string, signal: AbortSignal) {
  const id = readContract('Uuid', citationId);
  const value = readContract(
    'Citation',
    await requestJSON(`${projectPath(projectId)}/citations/${id}`, signal),
  );
  if (value.id !== id) throw new Error('引用响应不匹配。');
  return value;
}
export async function listGrants(
  projectId: string,
  kbId: string,
  page: number,
  signal: AbortSignal,
) {
  return pageScope(
    readContract(
      'KnowledgeGrantPage',
      await requestJSON(`${kbPath(projectId, kbId)}/grants?page=${page}&size=20`, signal),
    ),
    page,
    20,
  );
}
export async function setGrant(
  projectId: string,
  kbId: string,
  userId: string,
  body: SetKnowledgeGrant,
  signal: AbortSignal,
) {
  readContract('SetKnowledgeGrant', body);
  const value = readContract(
    'KnowledgeGrant',
    await requestJSON(
      `${kbPath(projectId, kbId)}/grants/${resourceId(userId)}`,
      signal,
      jsonBody('PUT', body),
    ),
  );
  if (value.userId !== userId || value.access !== body.access) throw new Error('授权响应不匹配。');
  return value;
}
export async function removeGrant(
  projectId: string,
  kbId: string,
  userId: string,
  signal: AbortSignal,
) {
  await requestJSON(`${kbPath(projectId, kbId)}/grants/${resourceId(userId)}`, signal, {
    method: 'DELETE',
  });
}
