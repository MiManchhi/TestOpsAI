const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const Ajv = require('ajv');
const root = path.resolve(__dirname, '../..');
const dir = path.join(root, 'docs/contracts');
const read = (name) => JSON.parse(fs.readFileSync(path.join(dir, name), 'utf8'));
const schema = read('schemas.json');
const examples = read('examples.json');
const pages = read('pages.json').pages;
const tools = read('tools.json').tools;
const ajv = new Ajv({ allErrors: true, schemaId: 'auto', jsonPointers: true });
assert(ajv.validateSchema(schema), ajv.errorsText());
ajv.addSchema(schema);
const validate = (name, value) => {
  const check = ajv.getSchema(`${schema.$id}#/definitions/${name}`);
  assert(check, `Unknown schema ${name}`);
  assert(check(value), `${name}: ${ajv.errorsText(check.errors)}`);
};
for (const name of Object.keys(schema.definitions)) {
  assert(ajv.getSchema(`${schema.$id}#/definitions/${name}`), `Cannot compile ${name}`);
}
for (const instance of examples.instances) validate(instance.schema, instance.data);
let refs = 0;
function checkRefs(value, document, file) {
  if (!value || typeof value !== 'object') return;
  if (value.$ref) {
    const [refFile, pointer] = value.$ref.split('#');
    const target = refFile ? read(refFile.replace('./', '')) : document;
    assert(pointer?.startsWith('/'), `Unsupported ref ${value.$ref}`);
    const result = pointer
      .slice(1)
      .split('/')
      .reduce((v, k) => v?.[k.replace(/~1/g, '/').replace(/~0/g, '~')], target);
    assert(result, `Unresolved ${file}: ${value.$ref}`);
    refs++;
  }
  for (const x of Object.values(value)) checkRefs(x, document, file);
}
const operationIds = new Set();
const publicIds = new Set();
const httpMethods = new Set(['get', 'post', 'put', 'patch', 'delete']);
let operationCount = 0;
for (const file of ['public.openapi.json', 'ai.openapi.json', 'tools.openapi.json']) {
  const document = read(file);
  assert.equal(document.openapi, '3.1.1');
  checkRefs(document, document, file);
  for (const [uri, methods] of Object.entries(document.paths)) {
    for (const [method, op] of Object.entries(methods)) {
      assert(httpMethods.has(method));
      assert(!operationIds.has(op.operationId), `Duplicate operationId ${op.operationId}`);
      operationIds.add(op.operationId);
      operationCount++;
      const pathParams = [...uri.matchAll(/\{([^}]+)\}/g)].map((m) => m[1]).sort();
      const declared = (op.parameters || [])
        .filter((p) => p.in === 'path')
        .map((p) => p.name)
        .sort();
      assert.deepEqual(declared, pathParams, `${uri}: path parameters`);
      assert(op.responses['401'] && op.responses['403'], `${uri}: missing authorization errors`);
      assert(['SPECIFIED', 'IMPLEMENTED'].includes(op['x-implementation']));
      if (file === 'public.openapi.json') {
        publicIds.add(op.operationId);
        if (!['login', 'currentUser'].includes(op.operationId)) {
          assert(uri.startsWith('/api/v2/'));
          assert.equal(op['x-implementation'], 'SPECIFIED');
        }
        if (op.operationId === 'login') assert.deepEqual(op.security, []);
        else assert(document.security?.[0].UserBearer);
        assert(op['x-pages']?.length, `${uri}: missing page ownership`);
        if (method === 'post' && !['login'].includes(op.operationId)) {
          assert(
            op.parameters?.some(
              (p) => p.in === 'header' && p.name === 'Idempotency-Key' && p.required,
            ),
            `${op.operationId}: unprotected mutation`,
          );
        }
      } else assert(uri.startsWith('/internal/v1/'));
      if (op.responses['200']?.content?.['text/event-stream']) {
        assert(op.responses['200'].content['text/event-stream']['x-event-schema']);
      }
    }
  }
}
const pageOps = new Set();
const pageIds = new Set();
for (const page of pages) {
  assert(!pageIds.has(page.id), `Duplicate page ${page.id}`);
  pageIds.add(page.id);
  assert(page.operationIds.length);
  for (const id of page.operationIds) {
    assert(publicIds.has(id), `Page ${page.id}: ${id}`);
    pageOps.add(id);
  }
}
for (const id of publicIds) assert(pageOps.has(id), `Public operation lacks page mapping: ${id}`);
for (const tool of tools) {
  checkRefs(tool, tool, 'tools.json');
  assert(['READ_ONLY', 'DRAFT_ONLY'].includes(tool.effect));
  assert(!/shell|execute|commit|sql/i.test(tool.name));
  if (tool.name !== 'search_knowledge') assert(operationIds.has(tool.operationId));
}
const instance = (name) => structuredClone(examples.instances.find((x) => x.schema === name).data);
const rejects = (name, value) =>
  assert.throws(() => validate(name, value), `${name} must reject malformed input`);
const malformedSelection = instance('SelectionCreate');
malformedSelection.filter = {};
rejects('SelectionCreate', malformedSelection);
rejects('SelectionCreate', { mode: 'EXPLICIT' });
rejects('SelectionCreate', { mode: 'FILTER', filter: {}, caseIds: ['1'] });
rejects('TestCase', { ...instance('TestCase'), id: 9007199254740993 });
rejects('TestCase', { ...instance('TestCase'), tags: 'smoke,regression' });
rejects('TestCase', { ...instance('TestCase'), lifecycleStatus: 'FAIL' });
rejects('Result', { ...instance('Result'), status: 'ACTIVE' });
rejects('MessageCreate', { ...instance('MessageCreate'), userId: '999' });
const noKnowledge = instance('MessageCreate');
noKnowledge.context.knowledgeMode = 'NONE';
rejects('MessageCreate', noKnowledge);
rejects('DraftCommit', { ...instance('DraftCommit'), approved: true });
rejects('ObjectRef', {
  objectId: '10000000-0000-4000-8000-000000000001',
  sha256: 'a'.repeat(64),
  sizeBytes: 10,
  logicalPath: '../secrets',
});
rejects('CaseFilter', { tagsMatch: 'SQL', sql: 'select 1' });
const sequence = examples.instances.filter((x) => x.schema === 'TurnEvent').map((x) => x.data);
function validateSequence(events) {
  assert(events.length > 1);
  assert.equal(events[0].event, 'turn.started');
  let terminal = false;
  events.forEach((event, index) => {
    validate('TurnEvent', event);
    assert.equal(event.turnId, events[0].turnId);
    assert.equal(event.sequence, index + 1);
    assert(!terminal, 'event after terminal');
    terminal = ['turn.completed', 'turn.failed', 'turn.cancelled'].includes(event.event);
  });
  assert(terminal, 'no terminal event');
}
validateSequence(sequence);
assert.throws(() => validateSequence([...sequence, sequence.at(-1)]));
assert.throws(() => validateSequence([sequence[0], sequence[2], sequence[3]]));
const run = instance('Run');
assert.equal(run.counts.total, run.counts.pass + run.counts.fail + run.counts.skip);
const results = examples.instances.filter((x) => x.schema === 'Result').map((x) => x.data);
for (const [key, status] of [
  ['pass', 'PASS'],
  ['fail', 'FAIL'],
  ['skip', 'SKIP'],
])
  assert.equal(run.counts[key], results.filter((x) => x.status === status).length);
const knownCases = new Set(
  examples.instances.filter((x) => x.schema === 'TestCase').map((x) => x.data.id),
);
for (const r of results) assert(r.caseId === null || knownCases.has(r.caseId));
console.log(
  `Contract checks passed: ${Object.keys(schema.definitions).length} schemas, ${operationCount} operations, ${refs} refs, ${pages.length} pages, ${examples.instances.length} examples; rejection and SSE sequence checks passed.`,
);
