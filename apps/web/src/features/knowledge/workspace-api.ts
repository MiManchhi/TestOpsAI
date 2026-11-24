import { jsonBody, pageScope, resourceId } from '../../app/api-contract';
import { requestJSON, requestResponse } from '../../app/http';
import {
  readWorkspaceContract,
  type DocumentKind,
  type DocumentReaders,
  type ReviewStatus,
} from '../../app/workspace-contracts';

const base = (projectId: string) => `/api/v3/projects/${resourceId(projectId)}`;
const document = (projectId: string, id: string) =>
  `${base(projectId)}/documents/${resourceId(id)}`;
const version = (projectId: string, id: string) => `${base(projectId)}/versions/${resourceId(id)}`;

const pagination = (page: number) => new URLSearchParams({ page: String(page), size: '20' });

export async function listDocuments(
  projectId: string,
  page: number,
  signal: AbortSignal,
  q = '',
  status?: ReviewStatus,
) {
  const query = pagination(page);
  if (q.trim()) query.set('q', q.trim());
  if (status) query.set('reviewStatus', status);
  return pageScope(
    readWorkspaceContract(
      'DocPage',
      await requestJSON(`${base(projectId)}/documents?${query}`, signal),
    ),
    page,
    20,
  );
}

export async function listDocumentVersions(
  projectId: string,
  id: string,
  page: number,
  signal: AbortSignal,
) {
  const result = pageScope(
    readWorkspaceContract(
      'DocPage',
      await requestJSON(`${document(projectId, id)}/versions?${pagination(page)}`, signal),
    ),
    page,
    20,
  );
  if (result.items.some((item) => item.documentId !== id))
    throw new Error('资料版本响应范围异常。');
  return result;
}

export async function uploadAttachment(
  projectId: string,
  file: File,
  key: string,
  signal: AbortSignal,
) {
  const body = new FormData();
  body.set('file', file);
  return readWorkspaceContract(
    'Attachment',
    await requestJSON(
      `${base(projectId)}/attachments`,
      signal,
      {
        method: 'POST',
        headers: { 'Idempotency-Key': key },
        body,
      },
      120_000,
    ),
  );
}

export type DocumentSubmission = {
  title: string;
  kind: DocumentKind;
  versionLabel: string;
  attachmentId: string;
  changeNote: string;
  documentId?: string;
};

export async function submitVersion(
  projectId: string,
  body: DocumentSubmission,
  key: string,
  signal: AbortSignal,
) {
  return readWorkspaceContract(
    'Doc',
    await requestJSON(`${base(projectId)}/documents`, signal, jsonBody('POST', body, key)),
  );
}

export async function getVersion(projectId: string, id: string, signal: AbortSignal) {
  const result = readWorkspaceContract('Doc', await requestJSON(version(projectId, id), signal));
  if (result.versionId !== id) throw new Error('资料版本响应范围异常。');
  return result;
}

export async function readVersion(
  projectId: string,
  id: string,
  signal: AbortSignal,
  query: { cursor?: string; locator?: string } = {},
) {
  const params = new URLSearchParams(query);
  const result = readWorkspaceContract(
    'Content',
    await requestJSON(`${version(projectId, id)}/content?${params}`, signal),
  );
  if (result.versionId !== id) throw new Error('正文响应范围异常。');
  return result;
}

export async function downloadOriginal(projectId: string, id: string, signal: AbortSignal) {
  const result = await requestResponse(`${version(projectId, id)}/original`, signal, {}, 120_000);
  const filename = result.headers
    .get('Content-Disposition')
    ?.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  const blob = await result.blob();
  signal.throwIfAborted();
  return { filename: filename ? decodeURIComponent(filename) : `original-${id}`, blob };
}

export async function reviewVersion(
  projectId: string,
  id: string,
  body: { decision: 'APPROVE' | 'RETURN'; note: string; revision: number },
  key: string,
  signal: AbortSignal,
) {
  return readWorkspaceContract(
    'Doc',
    await requestJSON(`${version(projectId, id)}/review`, signal, jsonBody('POST', body, key)),
  );
}

export async function retryIndex(projectId: string, id: string, key: string, signal: AbortSignal) {
  return readWorkspaceContract(
    'Receipt',
    await requestJSON(`${version(projectId, id)}/retry`, signal, {
      method: 'POST',
      headers: { 'Idempotency-Key': key },
    }),
  );
}

export async function getReaders(projectId: string, id: string, signal: AbortSignal) {
  return readWorkspaceContract(
    'Readers',
    await requestJSON(`${document(projectId, id)}/readers`, signal),
  );
}

export async function setReaders(
  projectId: string,
  id: string,
  body: DocumentReaders,
  key: string,
  signal: AbortSignal,
) {
  return readWorkspaceContract(
    'Readers',
    await requestJSON(`${document(projectId, id)}/readers`, signal, jsonBody('PUT', body, key)),
  );
}

export async function listBases(projectId: string, page: number, signal: AbortSignal) {
  return pageScope(
    readWorkspaceContract(
      'BasisPage',
      await requestJSON(`${base(projectId)}/bases?${pagination(page)}`, signal),
    ),
    page,
    20,
  );
}

export async function publishBasis(
  projectId: string,
  body: { label: string; versionIds: string[]; expectedRevision: number },
  key: string,
  signal: AbortSignal,
) {
  return readWorkspaceContract(
    'Basis',
    await requestJSON(`${base(projectId)}/bases`, signal, jsonBody('POST', body, key)),
  );
}

export async function listPlatformJobs(projectId: string, page: number, signal: AbortSignal) {
  const params = pagination(page);
  if (projectId) params.set('projectId', resourceId(projectId));
  return pageScope(
    readWorkspaceContract('JobPage', await requestJSON(`/api/v3/platform/jobs?${params}`, signal)),
    page,
    20,
  );
}
