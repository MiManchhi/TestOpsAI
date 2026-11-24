// Runs only in Vite/Vitest's Node process. Browser validators contain no runtime compiler.
import { createRequire } from 'node:module';
import Ajv from 'ajv';
import workspace from '../../../../docs/contracts/workspace.openapi.json';

type Compiled = Ajv.ValidateFunction & { source: { code: string }; refVal: unknown[] };

const workspaceNames = [
  'AccountPage',
  'Account',
  'ProjectPage',
  'Project',
  'UserPage',
  'MembershipPage',
  'Membership',
  'HandoverPage',
  'Handover',
  'Attachment',
  'Doc',
  'DocPage',
  'Content',
  'Readers',
  'Basis',
  'BasisPage',
  'Receipt',
  'JobPage',
  'Turn',
  'ConversationPage',
  'MessagePage',
  'Citation',
  'Event',
  'Run',
  'RunPage',
  'ResultPage',
  'ArtifactPage',
  'LogLinePage',
];
const workspaceId = 'urn:testops:workspace-v2';

export function buildContractValidators(): string {
  const ajv = new Ajv({ sourceCode: true, inlineRefs: false, allErrors: true, schemaId: 'auto' });
  ajv.addSchema({ $id: workspaceId, components: workspace.components });
  const ids = new Map<unknown, number>();
  const chunks: string[] = [];
  function serialize(value: unknown): string {
    if (typeof value !== 'function') return JSON.stringify(value) ?? 'undefined';
    if (ids.has(value)) return `validators[${ids.get(value)}]`;
    const index = ids.size;
    ids.set(value, index);
    // Ajv 6 sourceCode/refVal metadata is consumed only here, with the dependency locked.
    const compiled = value as Compiled;
    if (!compiled.source?.code || !compiled.refVal) throw new Error('Unsupported Ajv source');
    const refs = compiled.refVal.map(serialize);
    const source = compiled.source.code
      .replace(/var refVal\d+ = refVal\[\d+\];/g, '')
      .replace(/\brefVal\[(\d+)\]/g, (_match, id: string) => `(${refs[Number(id)]})`)
      .replace(/\brefVal(\d+)\b/g, (_match, id: string) => `(${refs[Number(id)]})`);
    const attachSchema = source.includes('validate.schema')
      ? `validate.schema = ${JSON.stringify(compiled.schema)}; `
      : '';
    chunks.push(
      `validators[${index}] = (() => { ${source.replace(/return validate;?\s*$/, `${attachSchema}return validate;`)} })();`,
    );
    return `validators[${index}]`;
  }
  const exports: string[] = [];
  for (const name of workspaceNames) {
    const validator = ajv.getSchema(`${workspaceId}#/components/schemas/${name}`);
    if (!validator) throw new Error(`Missing workspace schema: ${name}`);
    exports.push(`${JSON.stringify(`Workspace${name}`)}: ${serialize(validator)}`);
  }
  const require = createRequire(import.meta.url);
  const equal: unknown = require('fast-deep-equal');
  const length: unknown = require('ajv/lib/compile/ucs2length');
  // Only formats referenced by the frozen contract are emitted. No format compiler is shipped.
  const formats = (ajv as unknown as { _formats: Record<string, unknown> })._formats;
  const formatSource = ['uuid', 'date-time'].map((name) => {
    const format = formats[name];
    if (!(format instanceof RegExp)) throw new Error(`Unsupported frozen format: ${name}`);
    return `${JSON.stringify(name)}: ${format.toString()}`;
  });
  return `const validators = []; const equal = ${String(equal)}; const ucs2length = ${String(length)};
    const formats = {${formatSource.join(',')}}; ${chunks.join('\n')}
    export default {${exports.join(',')}};`;
}
