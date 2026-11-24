import { expect, it } from 'vitest';
import { runInNewContext } from 'node:vm';
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
  const empty = { items: [], page: 0, size: 20, total: 0 };
  for (const page of ['ProjectPage', 'ConversationPage', 'RunPage', 'DocPage', 'MembershipPage']) {
    const validate = validators?.['Workspace' + page];
    expect(validate?.(empty)).toBe(true);
    expect(validate?.({ ...empty, items: [{}] })).toBe(false);
    expect(validate?.({ ...empty, unexpected: true })).toBe(false);
  }
});
