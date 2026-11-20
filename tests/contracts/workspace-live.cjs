// Run only against the isolated final-version acceptance deployment. See the joint runbook.
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const example = require('../../docs/contracts/workspace.examples.json');
const spec = require('../../docs/contracts/workspace.openapi.json');
const Ajv = require('ajv');
const ajv = new Ajv({ allErrors: true });
ajv.addSchema({ $id: 'urn:testops:live', components: spec.components });
const validate = (name) => ajv.getSchema('urn:testops:live#/components/schemas/' + name);
const roles = ['ADMIN', 'OWNER', 'TEST', 'DEV'];
const base = (process.env.TESTOPS_REVIEW_BASE || '').replace(/\/$/, '');
const token = (name) => process.env['TESTOPS_REVIEW_' + name + '_JWT'];
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function api(role, method, path, body, status = 200, key = randomUUID()) {
  const headers = { Authorization: 'Bearer ' + token(role) };
  if (method !== 'GET') headers['Idempotency-Key'] = key;
  if (body && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  const res = await fetch(base + path, {
    method,
    headers,
    body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(150000),
  });
  const ct = res.headers.get('content-type') || '';
  const value = ct.includes('json') ? await res.json() : await res.text();
  assert.ok(
    [status].flat().includes(res.status),
    method +
      ' ' +
      path +
      ' HTTP ' +
      res.status +
      ' ' +
      (value?.code || '') +
      ' ' +
      (value?.requestId || ''),
  );
  return value;
}
async function until(get, ready) {
  const end = Date.now() + 150000;
  do {
    const v = await get();
    if (ready(v)) return v;
    if (
      v.indexStatus === 'FAILED' ||
      v.parseStatus === 'FAILED' ||
      v.status === 'FAILED' ||
      v.status === 'CANCELLED'
    )
      throw Error('Provider job ended ' + (v.errorCode || v.status));
    await sleep(500);
  } while (Date.now() < end);
  throw Error('Timed out waiting for provider');
}
async function run() {
  assert.ok(
    base && roles.every(token),
    'Set TESTOPS_REVIEW_BASE and four TESTOPS_REVIEW_{ROLE}_JWT variables for isolated fixtures',
  );
  const users = {};
  for (const role of roles) users[role] = String((await api(role, 'GET', '/api/v1/users/me')).id);
  assert.equal(new Set(Object.values(users)).size, 4, 'Four distinct actors are required');
  const payload = { ...example.project, name: '验收-' + randomUUID(), ownerId: users.OWNER };
  const key = randomUUID();
  const project = await api('ADMIN', 'POST', '/api/v3/platform/projects', payload, 201, key);
  assert.equal(
    (await api('ADMIN', 'POST', '/api/v3/platform/projects', payload, 201, key)).projectId,
    project.projectId,
  );
  const p = '/api/v3/projects/' + project.projectId;
  console.log('Created isolated project ' + project.projectId);
  await api('DEV', 'POST', '/api/v3/platform/projects', payload, 403);
  for (const role of ['TEST', 'DEV'])
    await api('OWNER', 'PUT', p + '/members/' + users[role], { role, revision: 0 });
  await api('ADMIN', 'GET', p + '/documents', undefined, [403, 404]);
  async function file(role, name, text) {
    const form = new FormData();
    form.append('file', new Blob([text]), name);
    return (await api(role, 'POST', p + '/attachments', form, 201)).attachmentId;
  }
  async function version(label, text, logical, kind = 'SPEC') {
    const attachmentId = await file('TEST', label + '.md', text);
    const obj = await api(
      'TEST',
      'POST',
      p + '/documents',
      {
        title: kind === 'TENDER' ? '验收标书' : '响应时间',
        kind,
        versionLabel: label,
        attachmentId,
        changeNote: '验收样例',
        ...(logical ? { documentId: logical } : {}),
      },
      201,
    );
    await api('DEV', 'GET', p + '/versions/' + obj.versionId, undefined, 404);
    const v = await api('OWNER', 'GET', p + '/versions/' + obj.versionId);
    assert.equal(v.reviewStatus, 'PENDING');
    assert.equal(v.indexStatus, 'NOT_STARTED');
    assert.equal(await api('OWNER', 'GET', p + '/versions/' + v.versionId + '/original'), text);
    await api('DEV', 'GET', p + '/versions/' + v.versionId + '/original', undefined, 404);
    await api('OWNER', 'POST', p + '/versions/' + v.versionId + '/review', {
      decision: 'APPROVE',
      note: '核对原文',
      revision: v.revision,
    });
    return until(
      () => api('TEST', 'GET', p + '/versions/' + obj.versionId),
      (x) => x.indexStatus === 'READY' && x.reviewStatus === 'APPROVED',
    );
  }
  const v1 = await version('V1', example.sourceV1);
  const tender = await version('V1', example.sourceShared, undefined, 'TENDER');
  const first = await api(
    'OWNER',
    'POST',
    p + '/bases',
    { label: '验收包 V1', versionIds: [v1.versionId, tender.versionId], expectedRevision: 0 },
    201,
  );
  const outputAttachmentId = await file('DEV', 'output.xml', example.xml),
    logAttachmentId = await file('DEV', 'run.log', example.log);
  const registration = {
    name: '响应时间复测',
    executedAt: first.effectiveFrom,
    outputAttachmentId,
    logAttachmentIds: [logAttachmentId],
  };
  const runKey = randomUUID();
  const result = await api('DEV', 'POST', p + '/runs', registration, 202, runKey);
  assert.equal(
    (await api('DEV', 'POST', p + '/runs', registration, 202, runKey)).runId,
    result.runId,
  );
  const parsed = await until(
    () => api('DEV', 'GET', p + '/runs/' + result.runId),
    (x) => x.parseStatus === 'READY',
  );
  assert.equal(parsed.pass, 1);
  assert.equal(parsed.fail, 0);
  const v2 = await version('V2', example.sourceV2, v1.documentId);
  assert.deepEqual([...first.versionIds].sort(), [v1.versionId, tender.versionId].sort());
  await api(
    'OWNER',
    'POST',
    p + '/bases',
    {
      label: '含同一资料两版',
      versionIds: [v1.versionId, v2.versionId],
      expectedRevision: first.revision,
    },
    [400, 409, 422],
  );
  const currentBasis = await api(
    'OWNER',
    'POST',
    p + '/bases',
    {
      label: '验收包 V2',
      versionIds: [v2.versionId, tender.versionId],
      expectedRevision: first.revision,
    },
    201,
  );
  assert.deepEqual([...currentBasis.versionIds].sort(), [v2.versionId, tender.versionId].sort());
  const turn = await api(
    'DEV',
    'POST',
    p + '/turns',
    { ...example.turn, clientMessageId: randomUUID(), runId: result.runId },
    202,
  );
  const sse = await api('DEV', 'GET', p + '/turns/' + turn.turnId + '/events');
  const events = sse
    .replace(/\r\n/g, '\n')
    .split('\n\n')
    .map((block) =>
      block
        .split('\n')
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).trimStart())
        .join('\n'),
    )
    .filter(Boolean)
    .map((x) => JSON.parse(x));
  for (const [i, event] of events.entries()) {
    assert.ok(validate('Event')(event));
    assert.equal(event.sequence, i + 1);
  }
  assert.equal(events.at(-1)?.event, 'turn.completed');
  assert.equal(events.filter((e) => e.event === 'turn.completed').length, 1);
  const a = events.at(-1).data.answer;
  assert.ok(validate('Answer')(a));
  assert.equal(a.robotStatus, 'PASS');
  assert.ok(a.findings.some((f) => f.basis === 'AT_RUN' && f.verdict === 'MEETS'));
  assert.ok(a.findings.some((f) => f.basis === 'CURRENT' && f.verdict === 'DOES_NOT_MEET'));
  for (const f of a.findings.filter((f) => ['MEETS', 'DOES_NOT_MEET'].includes(f.verdict))) {
    const refs = a.citations.filter((c) => f.citationIds.includes(c.citationId));
    assert.ok(refs.some((c) => c.source.resourceKind === 'DOCUMENT'));
    assert.ok(refs.some((c) => c.source.resourceKind === 'RUN_LOG'));
  }
  const citation = a.citations.find(
    (c) =>
      c.source.resourceKind === 'DOCUMENT' &&
      [v1.versionId, v2.versionId].includes(c.source.resourceId),
  );
  assert.ok(citation);
  await api('DEV', 'GET', p + '/citations/' + citation.citationId);
  await api(
    'TEST',
    'GET',
    p + '/conversations/' + turn.conversationId + '/messages',
    undefined,
    404,
  );
  const readers = await api('OWNER', 'GET', p + '/documents/' + v1.documentId + '/readers');
  await api('OWNER', 'PUT', p + '/documents/' + v1.documentId + '/readers', {
    visibility: 'RESTRICTED',
    userIds: [users.OWNER, users.TEST],
    revision: readers.revision,
  });
  await api('DEV', 'GET', p + '/citations/' + citation.citationId, undefined, 404);
  const jobPage = await api('ADMIN', 'GET', '/api/v3/platform/jobs?projectId=' + project.projectId);
  assert.ok(validate('JobPage')(jobPage));
  const projectAdmin = '/api/v3/platform/projects/' + project.projectId;
  const prior = await api('OWNER', 'GET', p);
  const transfer = await api(
    'OWNER',
    'POST',
    p + '/owner-transfers',
    { newOwnerId: users.DEV, formerRole: 'TEST', revision: prior.revision },
    201,
  );
  assert.equal(transfer.status, 'PENDING');
  assert.equal((await api('OWNER', 'GET', p)).owner.userId, users.OWNER);
  await api('DEV', 'GET', p + '/versions/' + v1.versionId, undefined, 404);
  const accepted = await api(
    'ADMIN',
    'POST',
    projectAdmin + '/owner-transfers/' + transfer.handoverId + '/decision',
    { decision: 'APPROVE', note: '项目交接', revision: transfer.revision },
  );
  assert.equal(accepted.status, 'APPROVED');
  assert.equal(accepted.mode, 'OWNER_REQUEST');
  const next = await api('DEV', 'GET', p);
  assert.equal(next.owner.userId, users.DEV);
  assert.ok(
    (await api('DEV', 'GET', p + '/documents/' + v1.documentId + '/readers')).userIds.includes(
      users.DEV,
    ),
  );
  await api('OWNER', 'GET', p + '/owner-transfers', undefined, 403);
  const proxy = {
    newOwnerId: users.TEST,
    formerRole: 'DEV',
    revision: next.revision,
    unavailableReason: '',
  };
  await api('ADMIN', 'POST', projectAdmin + '/owner-transfers/proxy', proxy, 400);
  const replaced = await api(
    'ADMIN',
    'POST',
    projectAdmin + '/owner-transfers/proxy',
    { ...proxy, unavailableReason: '隔离验收样例：负责人已离岗' },
    201,
  );
  assert.equal(replaced.mode, 'ADMIN_PROXY');
  assert.equal((await api('TEST', 'GET', p)).owner.userId, users.TEST);
  console.log(
    'PASS: project/member, approved-only states, two-file bases, original run, event stream, citation/revocation, reviewed and proxy handover',
  );
}
run().catch((err) => {
  console.error(err.message);
  process.exitCode = 1;
});
