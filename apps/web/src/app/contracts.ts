// Generated from frozen docs/contracts/schemas.json 1.0.0; runtime validation uses the same source.
import Ajv from 'ajv';
import schema from '../../../../docs/contracts/schemas.json';

export type Id = string;
export type Project = {
  id: Id;
  key: string;
  name: string;
  description: string;
  role: 'OWNER' | 'EDITOR' | 'VIEWER';
  createdAt: Timestamp;
};
export type Timestamp = string;
export type Suite = {
  id: Id;
  projectId: Id;
  parentId: Id | null;
  name: string;
  sourcePath: string;
  revision: Sha256;
  caseCount: number;
};
export type Sha256 = string;
export type TestCase = {
  id: Id;
  projectId: Id;
  suiteId: Id | null;
  stableKey: string;
  title: string;
  module: string;
  tags: Array<string>;
  priority: 'P0' | 'P1' | 'P2' | 'P3';
  lifecycleStatus: 'ACTIVE' | 'ARCHIVED';
  sourceType: 'ROBOT' | 'MANUAL';
  documentation: string;
  expectedResult: string;
  sourceRef: SourceRef | null;
  steps: Array<Step>;
  version: number;
  updatedAt: Timestamp;
};
export type SourceRef = { path: string; line: number; revision: Sha256 };
export type Step = { keyword: string; arguments: Array<string>; line: number };
export type CaseFilter = {
  suiteId?: Id;
  includeDescendants?: boolean;
  module?: string;
  query?: string;
  tags?: Array<string>;
  tagsMatch?: 'ALL' | 'ANY';
  priority?: 'P0' | 'P1' | 'P2' | 'P3';
  lifecycleStatus?: 'ACTIVE' | 'ARCHIVED';
  sourceType?: 'ROBOT' | 'MANUAL';
};
export type SelectionCreate = {
  mode: 'EXPLICIT' | 'FILTER';
  caseIds?: Array<Id>;
  filter?: CaseFilter;
  excludedCaseIds?: Array<Id>;
} & (
  | { mode?: 'EXPLICIT'; caseIds: Array<Id>; filter?: never }
  | { mode?: 'FILTER'; filter: CaseFilter; caseIds?: never }
);
export type Selection = {
  id: Uuid;
  projectId: Id;
  count: number;
  cases: Array<CaseVersion>;
  digest: Sha256;
  expiresAt: Timestamp;
};
export type Uuid = string;
export type CaseVersion = { caseId: Id; version: number };
export type ProjectPage = { items: Array<Project>; total: number; page: number; size: number };
export type SuitePage = { items: Array<Suite>; total: number; page: number; size: number };
export type TestCasePage = { items: Array<TestCase>; total: number; page: number; size: number };
type ContractTypes = {
  Id: Id;
  Project: Project;
  Suite: Suite;
  TestCase: TestCase;
  CaseFilter: CaseFilter;
  SelectionCreate: SelectionCreate;
  Selection: Selection;
  ProjectPage: ProjectPage;
  SuitePage: SuitePage;
  TestCasePage: TestCasePage;
};

const ajv = new Ajv({ allErrors: true, schemaId: 'auto' });
ajv.addSchema(schema);
export function readContract<K extends keyof ContractTypes>(
  name: K,
  value: unknown,
): ContractTypes[K] {
  const check = ajv.getSchema(schema.$id + '#/definitions/' + name);
  if (!check || !check(value))
    throw new Error('服务响应或请求格式不符合约定，请重试或联系管理员。');
  // The assertion follows validation against the exact schema that generated these types.
  return value as ContractTypes[K];
}
