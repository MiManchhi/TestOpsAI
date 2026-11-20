const assert = require('node:assert/strict');
const Ajv = require('ajv');
const spec = require('../../docs/contracts/workspace.openapi.json');
const example = require('../../docs/contracts/workspace.examples.json');
const url = 'https://testops.invalid/workspace-contract';
const ajv = new Ajv({ allErrors: true, schemaId: 'auto' });
// OpenAPI describes the multipart file part as binary. Live tests send actual bytes.
ajv.addFormat('password', (value) => typeof value === 'string' && value.length > 0);
ajv.addFormat('binary', (value) => Buffer.byteLength(value) > 0);
ajv.addSchema({ $id: url, components: spec.components }, url);
const schemas = spec.components.schemas;
const verify = (name) => ajv.getSchema(url + '#/components/schemas/' + name);
function check(name, value, expected = true) {
  const valid = verify(name);
  assert.equal(valid(value), expected, name + ' ' + JSON.stringify(valid.errors));
}
for (const model of Object.keys(schemas)) assert.equal(typeof verify(model), 'function', model);
function walk(value) {
  if (!value || typeof value !== 'object') return;
  if (value.$ref) assert.ok(schemas[value.$ref.split('/').pop()], value.$ref);
  Object.values(value).forEach(walk);
}
walk(spec);
const ids = new Set();
for (const [path, item] of Object.entries(spec.paths)) {
  for (const [method, op] of Object.entries(item)) {
    assert.ok(!ids.has(op.operationId), op.operationId);
    ids.add(op.operationId);
    const internal = path.startsWith('/internal/');
    assert.deepEqual(
      op.security,
      internal ? [{ serviceJwt: [], delegation: [] }] : [{ userJwt: [] }],
    );
    if (!internal && method !== 'get')
      assert.ok(
        op.parameters.some((p) => p.name === 'Idempotency-Key' && p.required),
        op.operationId,
      );
    assert.deepEqual(
      op.parameters
        .filter((p) => p.in === 'path')
        .map((p) => p.name)
        .sort(),
      Array.from(path.matchAll(/\{(\w+)\}/g), (m) => m[1]).sort(),
    );
    for (const p of op.parameters.filter((p) => p.in === 'path')) assert.ok(p.required);
    if (path.startsWith('/api/v3/platform/')) assert.equal(op['x-access'], 'admin');
  }
}
const bindings = require('../../docs/contracts/workspace.bindings.json');
assert.deepEqual(Object.keys(bindings).sort(), [...ids].sort());
for (const [id, entry] of Object.entries(bindings)) {
  assert.equal(entry.provider.split('#')[1], id);
  assert.ok(entry.consumer && entry.work && entry.accept, id);
}
check('BasisSet', example.basisPublish);
for (const versionIds of [[], ['12', '12']])
  check('BasisSet', { ...example.basisPublish, versionIds }, false);
check('BasisSet', { topic: '响应时间', versionId: '12', expectedRevision: 1 }, false);
const { basisAtRun, basisCurrent, versions, scope } = example.context;
for (const group of [basisAtRun, basisCurrent]) {
  check('VisibleBasis', group);
  assert.ok(group.visibleVersionIds.length >= 2);
  assert.ok(group.visibleVersionIds.every((id) => scope.versionIds.includes(id)));
}
check('VisibleBasis', { ...basisCurrent, coverage: 'PARTIAL', visibleVersionIds: [] });
check('VisibleBasis', { ...basisCurrent, visibleVersionIds: [] }, false);
check('VisibleBasis', { ...basisCurrent, hiddenVersionIds: ['44'] }, false);
assert.equal(
  versions.find((v) => v.versionId === '13').contentHash,
  require('node:crypto').createHash('sha256').update(example.sourceShared).digest('hex'),
);
const sampleDoc = {
  documentId: '1',
  versionId: '12',
  title: '规范',
  kind: 'SPEC',
  versionLabel: 'V2',
  reviewStatus: 'PENDING',
  indexStatus: 'NOT_STARTED',
  visibility: 'PROJECT',
  submittedBy: { userId: '1', displayName: '李宁' },
  revision: 1,
  reason: '',
  createdAt: example.events[0].occurredAt,
  errorCode: null,
};
for (const reviewStatus of ['PENDING', 'RETURNED', 'APPROVED']) {
  for (const indexStatus of ['NOT_STARTED', 'QUEUED', 'PROCESSING', 'READY', 'FAILED']) {
    check(
      'Doc',
      {
        ...sampleDoc,
        reviewStatus,
        indexStatus,
        reason: reviewStatus === 'RETURNED' ? '版本不符' : '',
      },
      (reviewStatus === 'APPROVED') === (indexStatus !== 'NOT_STARTED'),
    );
  }
}
check('Doc', { ...sampleDoc, reviewStatus: 'RETURNED', reason: ' ' }, false);
check('Review', { decision: 'RETURN', note: '  ', revision: 1 }, false);
check(
  'ProxyTransfer',
  { newOwnerId: '3', formerRole: 'TEST', revision: 1, unavailableReason: ' ' },
  false,
);
const transfer = {
  handoverId: example.turn.clientMessageId,
  projectId: '1',
  oldOwnerId: '2',
  newOwnerId: '3',
  formerRole: 'TEST',
  mode: 'OWNER_REQUEST',
  status: 'PENDING',
  requestedBy: '2',
  decidedBy: null,
  reason: '',
  revision: 1,
  createdAt: sampleDoc.createdAt,
};
check('Handover', transfer);
check('Handover', { ...transfer, mode: 'ADMIN_PROXY' }, false);
check('Handover', { ...transfer, status: 'APPROVED' }, false);
check('Handover', {
  ...transfer,
  mode: 'ADMIN_PROXY',
  status: 'APPROVED',
  requestedBy: '1',
  decidedBy: '1',
  reason: '原负责人已离岗',
});
check('IndexBlockPage', {
  jobId: transfer.handoverId,
  versionId: '12',
  contentHash: versions[1].contentHash,
  parserVersion: 'knowledge-v2',
  blocks: [{ locator: 'line 2', text: example.sourceV2.split('\n')[1] }],
  nextCursor: null,
});
const serializedScope = JSON.stringify([
  scope.projectId,
  scope.turnId,
  scope.authRevision,
  ...[scope.versionIds, scope.runIds, scope.attachmentIds].map((ids) => [...new Set(ids)].sort()),
]);
assert.equal(
  require('node:crypto').createHash('sha256').update(serializedScope).digest('hex'),
  example.scopeDigest,
);
check('Context', example.context);
check('Candidate', example.candidate);
for (const [i, raw] of [example.sourceV1, example.sourceV2, example.log].entries()) {
  const citation = example.answer.citations[i];
  const line = Number(citation.source.locator.split(' ')[1]);
  assert.equal(raw.split('\n')[line - 1], citation.excerpt);
  if (i < 2) {
    const hash = require('node:crypto').createHash('sha256').update(raw).digest('hex');
    assert.equal(example.context.versions[i].contentHash, hash);
  }
}
check('ProjectCreate', example.project);
check('MemberWrite', example.member);
check('TurnInput', example.turn);
check('Answer', example.answer);
const ref = example.answer.citations[0];
check('Citation', {
  ...ref,
  source: { ...ref.source, resourceKind: 'USER_MESSAGE' },
  projectSource: null,
});
check('Citation', { ...ref, source: { ...ref.source, resourceKind: 'CHAT_ATTACHMENT' } }, false);
check('Citation', { ...ref, projectSource: null }, false);
const citationIds = new Set(example.answer.citations.map((c) => c.citationId));
for (const finding of example.answer.findings) {
  for (const id of finding.citationIds) assert.ok(citationIds.has(id));
  const metric = finding.metric;
  assert.equal(metric.actual <= metric.limit, finding.verdict === 'MEETS');
}
for (const [i, event] of example.events.entries()) {
  check('Event', event);
  assert.equal(event.sequence, i + 1);
}
assert.equal(example.events.at(-1).event, 'turn.completed');
check('MemberWrite', { role: 'OWNER', revision: 0 }, false);
check('TurnInput', { ...example.turn, text: '', attachmentIds: [] }, false);
check('TurnInput', { ...example.turn, knowledgeMode: 'SELECTED' }, false);
check('Event', { ...example.events[0], event: 'turn.completed' }, false);
check('Readers', { visibility: 'RESTRICTED', userIds: [], revision: 1 }, false);
check(
  'Job',
  {
    jobId: example.turn.clientMessageId,
    projectId: '1',
    operation: 'INDEX',
    stage: 'PARSING',
    status: 'FAILED',
    errorCode: 'PARSE_FORMAT',
    updatedAt: example.events[0].occurredAt,
    excerpt: 'private',
  },
  false,
);
console.log(
  ids.size +
    ' operations, ' +
    Object.keys(schemas).length +
    ' schemas; references, auth/HTTP rules, examples and negative cases passed.',
);
