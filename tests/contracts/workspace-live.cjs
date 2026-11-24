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
const knowledgeOnly = process.argv.includes('--knowledge-only');
const base = (process.env.TESTOPS_REVIEW_BASE || '').replace(/\/$/, '');
const token = (name) => process.env['TESTOPS_REVIEW_' + name + '_JWT'];
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function readEvents(role, projectPath, turnId, lastEventId = 0) {
  const res = await fetch(base + projectPath + '/turns/' + turnId + '/events', {
    headers: { Authorization: 'Bearer ' + token(role), 'Last-Event-ID': String(lastEventId) },
    signal: AbortSignal.timeout(150000),
  });
  assert.equal(res.status, 200);
  assert.match(res.headers.get('content-type') ?? '', /text\/event-stream/);
  const blocks = (await res.text()).replace(/\r\n/g, '\n').split('\n\n');
  return blocks.flatMap((block) => {
    const data = block
      .split('\n')
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trimStart());
    return data.length ? [JSON.parse(data.join('\n'))] : [];
  });
}
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
    await api('OWNER', 'GET', p + '/versions/' + v.versionId + '/content', undefined, 404);
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
  if (knowledgeOnly) {
    const current = await api(
      'DEV',
      'GET',
      p + '/versions/' + v2.versionId + '/content?locator=line%202',
    );
    assert.ok(validate('Content')(current));
    assert.equal(current.blocks[0].text, example.sourceV2.split('\n')[1]);
    const history = await api('OWNER', 'GET', p + '/bases');
    assert.equal(history.items[1].effectiveTo, history.items[0].effectiveFrom);
    const readerPath = p + '/documents/' + v1.documentId + '/readers';
    const priorReaders = await api('OWNER', 'GET', readerPath);
    const restricted = await api('OWNER', 'PUT', readerPath, {
      visibility: 'RESTRICTED',
      userIds: [users.OWNER, users.TEST],
      revision: priorReaders.revision,
    });
    assert.ok(validate('Readers')(restricted));
    for (const id of [v1.versionId, v2.versionId]) {
      await api('DEV', 'GET', p + '/versions/' + id + '/content', undefined, 404);
      await api('DEV', 'GET', p + '/versions/' + id + '/original', undefined, 404);
      await api('TEST', 'GET', p + '/versions/' + id + '/content');
    }
    const jobs = await api('ADMIN', 'GET', '/api/v3/platform/jobs?projectId=' + project.projectId);
    assert.ok(validate('JobPage')(jobs));
    assert.ok(jobs.items.some((item) => item.operation === 'INDEX' && item.status === 'READY'));
    console.log(
      'PASS: approved document indexing, original line, basis history and reader revocation',
    );
    return;
  }
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
  const turn = await api(
    'DEV',
    'POST',
    p + '/turns',
    {
      attachmentIds: [],
      text: '最近一次复测的响应时间达标吗？请对照当时与当前的验收依据。',
      clientMessageId: randomUUID(),
    },
    202,
  );
  const events = await readEvents('DEV', p, turn.turnId);
  for (const [i, event] of events.entries()) {
    assert.ok(validate('Event')(event));
    assert.equal(event.sequence, i + 1);
  }
  assert.equal(events.at(-1)?.event, 'turn.completed');
  assert.equal(events.filter((e) => e.event === 'turn.completed').length, 1);
  assert.deepEqual(await readEvents('DEV', p, turn.turnId, events[0].sequence), events.slice(1));
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
  const follow = await api(
    'DEV',
    'POST',
    p + '/turns',
    {
      conversationId: turn.conversationId,
      clientMessageId: randomUUID(),
      attachmentIds: [],
      text: '按刚才未达标的要求，补写一条 Robot 回归用例，并给出依据。',
    },
    202,
  );
  const completed = (await readEvents('DEV', p, follow.turnId)).at(-1);
  assert.ok(completed);
  assert.equal(completed.event, 'turn.completed');
  const draft = completed.data.answer;
  assert.ok(validate('Answer')(draft));
  assert.match(draft.robotDraft ?? '', /\*\*\* Test Cases \*\*\*/i);
  assert.ok(
    draft.citations.some(
      (c) => c.source.resourceKind === 'DOCUMENT' && c.source.resourceId === v2.versionId,
    ),
    'The follow-up must cite the version whose requirement was missed',
  );
  assert.notEqual(draft.summary, a.summary);
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
  const hiddenHistory = await api(
    'DEV',
    'GET',
    p + '/conversations/' + turn.conversationId + '/messages',
  );
  assert.ok(validate('MessagePage')(hiddenHistory));
  const hiddenAnswer = hiddenHistory.items.find((item) => item.author === 'ASSISTANT');
  assert.ok(hiddenAnswer);
  assert.equal(hiddenAnswer.answer, null);
  assert.match(hiddenAnswer.text, /权限已变化/);
  const restrictedReplay = await readEvents('DEV', p, turn.turnId, events.at(-2).sequence);
  assert.equal(restrictedReplay.length, 1);
  assert.equal(restrictedReplay[0].event, 'turn.failed');
  assert.match(JSON.stringify(restrictedReplay[0].data), /AUTHORIZATION_CHANGED/);
  assert.doesNotMatch(JSON.stringify(restrictedReplay[0].data), /抄读指令|设备应在/);

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
