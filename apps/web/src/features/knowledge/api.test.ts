import { afterEach, expect, it, vi } from 'vitest';
import examples from '../../../../../docs/contracts/examples.json';
import { requestJSON } from '../../app/http';
import { readContract } from '../../app/contracts';
import {
  createKnowledgeBase,
  getCitation,
  getDocumentContent,
  listDocuments,
  setGrant,
  uploadDocument,
  validateDocumentFile,
} from './api';
vi.mock('../../app/http', () => ({ requestJSON: vi.fn() }));
afterEach(() => vi.resetAllMocks());
const signal = () => new AbortController().signal;
const kb = readContract(
  'KnowledgeBase',
  examples.instances.find((item) => item.schema === 'KnowledgeBase')?.data,
);
const document = readContract(
  'Document',
  examples.instances.find((item) => item.schema === 'Document')?.data,
);
const citation = readContract(
  'Citation',
  examples.instances.find((item) => item.schema === 'Citation')?.data,
);
it('validates project scope and request DTO before writing a knowledge base', async () => {
  vi.mocked(requestJSON).mockResolvedValue(kb);
  await createKnowledgeBase(
    '1',
    { name: 'Guide', category: 'PROTOCOL', visibility: 'RESTRICTED' },
    citation.id,
    signal(),
  );
  expect(vi.mocked(requestJSON).mock.calls[0]?.[2]?.headers).toMatchObject({
    'Idempotency-Key': citation.id,
  });
  vi.mocked(requestJSON).mockResolvedValue({ ...kb, projectId: '2' });
  await expect(
    createKnowledgeBase(
      '1',
      { name: 'Guide', category: 'PROTOCOL', visibility: 'RESTRICTED' },
      citation.id,
      signal(),
    ),
  ).rejects.toThrow('项目范围');
});
it('applies Markdown/PDF limits and sends file/title/version as multipart', async () => {
  expect(() => validateDocumentFile(new File(['x'], 'bad.zip'))).toThrow();
  expect(() =>
    validateDocumentFile(new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'big.md')),
  ).toThrow('2 MiB');
  expect(() =>
    validateDocumentFile(new File([new Uint8Array(20 * 1024 * 1024 + 1)], 'big.pdf')),
  ).toThrow('20 MiB');
  vi.mocked(requestJSON).mockResolvedValue({ ...document, status: 'QUEUED', stage: 'UPLOADING' });
  await uploadDocument(
    '1',
    '1',
    new File(['# Source'], 'guide.md'),
    'Guide',
    'v1',
    citation.id,
    signal(),
  );
  const options = vi.mocked(requestJSON).mock.calls[0]?.[2];
  expect(options?.body).toBeInstanceOf(FormData);
  const body = options?.body as FormData;
  expect(body.get('title')).toBe('Guide');
  expect(body.get('versionLabel')).toBe('v1');
  expect(options?.headers).toEqual({ 'Idempotency-Key': citation.id });
});
it('rejects documents outside the selected knowledge base and invalid grant responses', async () => {
  vi.mocked(requestJSON).mockResolvedValue({
    items: [{ ...document, knowledgeBaseId: '2' }],
    total: 1,
    page: 0,
    size: 20,
  });
  await expect(listDocuments('1', '1', 0, signal())).rejects.toThrow('范围');
  vi.mocked(requestJSON).mockResolvedValue({ userId: '3', access: 'READ' });
  await expect(setGrant('1', '1', '2', { access: 'READ' }, signal())).rejects.toThrow('授权响应');
});
it('encodes cursor data and never uses cached citations after revocation', async () => {
  const content = examples.instances.find((item) => item.schema === 'DocumentContent')?.data;
  vi.mocked(requestJSON).mockResolvedValueOnce(content);
  await getDocumentContent('1', '1', '1', 'next+/&?#', signal());
  expect(vi.mocked(requestJSON).mock.calls[0]?.[0]).toContain('cursor=next%2B%2F%26%3F%23');
  vi.mocked(requestJSON)
    .mockResolvedValueOnce(citation)
    .mockRejectedValueOnce(new Error('not accessible'));
  await getCitation('1', citation.id, signal());
  await expect(getCitation('1', citation.id, signal())).rejects.toThrow('not accessible');
});
