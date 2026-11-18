// Test-only fixtures from the frozen, self-authored examples. No production imports.
import examples from '../../../../../docs/contracts/examples.json';
import { readContract } from '../../app/contracts';
export const sampleProject = readContract(
  'Project',
  examples.instances.find((entry) => entry.schema === 'Project')?.data,
);
export const sampleCase = readContract(
  'TestCase',
  examples.instances.find((entry) => entry.schema === 'TestCase')?.data,
);
export function casePage(page = 0, total = 1, id = '1') {
  return { items: [{ ...sampleCase, id }], page, size: 20, total };
}
export function selection(ids = ['1']) {
  return readContract('Selection', {
    id: '00000000-0000-4000-8000-000000000001',
    projectId: '1',
    count: ids.length,
    cases: ids.map((caseId) => ({ caseId, version: 1 })),
    digest: 'a'.repeat(64),
    expiresAt: new Date(Date.now() + 86_400_000).toISOString(),
  });
}
