import { expect, it } from 'vitest';
import { runInNewContext } from 'node:vm';
import Ajv from 'ajv';
import schema from '../../../../docs/contracts/schemas.json';
import examples from '../../../../docs/contracts/examples.json';
import { buildContractValidators } from './contract-build';

it('validates frozen examples and malformed responses with string code generation disabled', () => {
  const source = buildContractValidators();
  expect(source).not.toMatch(/\bnew Function\b|\beval\s*\(/);
  const sandbox: { output?: Record<string, (data: unknown) => boolean> } = {};
  runInNewContext(source.replace('export default', 'globalThis.output ='), sandbox, {
    contextCodeGeneration: { strings: false, wasm: false },
  });
  const validators = sandbox.output;
  expect(validators).toBeDefined();
  const ajv = new Ajv({ allErrors: true, schemaId: 'auto' });
  ajv.addSchema(schema);
  let checked = 0;
  for (const example of examples.instances) {
    const validate = validators?.[example.schema];
    if (!validate) continue;
    expect(validate(example.data), example.schema).toBe(true);
    const reference = ajv.getSchema(`${schema.$id}#/definitions/${example.schema}`);
    for (const invalid of [null, [], {}, 1, { ...example.data, unexpected: true }]) {
      expect(validate(invalid), example.schema).toBe(Boolean(reference?.(invalid)));
    }
    checked++;
  }
  expect(checked).toBeGreaterThan(10);
  expect(
    validators?.Project?.({
      id: '1',
      key: 'X',
      name: 'X',
      description: '',
      role: 'INVALID',
      createdAt: 'invalid',
    }),
  ).toBe(false);
});
