const assert = require('node:assert/strict');
const Ajv = require('ajv');
const schema = require('../../docs/contracts/schemas.json');
const documents = [
  require('../../docs/contracts/public.openapi.json'),
  require('../../docs/contracts/ai.openapi.json'),
  require('../../docs/contracts/tools.openapi.json'),
];
const ajv = new Ajv({ allErrors: true, schemaId: 'auto' });
ajv.addSchema(schema);
for (const name of Object.keys(schema.definitions)) {
  assert.ok(ajv.getSchema(schema.$id + '#/definitions/' + name), name);
}
const ids = new Set();
function visit(value, spec) {
  if (!value || typeof value !== 'object') return;
  if (value.$ref) {
    const [file, path] = value.$ref.split('#');
    assert.ok(file === './schemas.json' || !file, value.$ref);
    assert.ok(
      path
        .slice(1)
        .split('/')
        .reduce((v, k) => v?.[k], file ? schema : spec),
      value.$ref,
    );
  }
  Object.values(value).forEach((v) => visit(v, spec));
}
for (const spec of documents) {
  visit(spec, spec);
  for (const [path, methods] of Object.entries(spec.paths)) {
    for (const op of Object.values(methods)) {
      assert.ok(!ids.has(op.operationId));
      ids.add(op.operationId);
      if (path.startsWith('/internal/')) {
        const [auth] = op.security ?? spec.security;
        assert.deepEqual(auth.ServiceBearer, [], path);
        if (path !== '/internal/v1/index-jobs/{jobId}') {
          assert.ok(
            auth.Delegation ||
              op.parameters?.some((p) => p.name === 'X-Delegation-Token' && p.required),
            path,
          );
        }
      }
    }
  }
}
console.log(ids.size + ' authentication, ingestion, result and object operations verified.');
