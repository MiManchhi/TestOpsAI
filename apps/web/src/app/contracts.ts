// Generated from frozen docs/contracts/schemas.json 1.0.0; build-time validators use the same source.
import validators from 'virtual:testops-contracts';
export type Id = string;
export type Uuid = string;
export type Timestamp = string;
export type Sha256 = string;
export type Error = { code: string; message: string; requestId: string };
export type Member = { userId: Id; username: string; role: 'OWNER' | 'EDITOR' | 'VIEWER' };
export type SetMember = { role: 'EDITOR' | 'VIEWER' };
export type ProjectCreate = { key: string; name: string; description?: string };
export type ProjectPatch = { name?: string; description?: string };
export type Project = {
  id: Id;
  key: string;
  name: string;
  description: string;
  role: 'OWNER' | 'EDITOR' | 'VIEWER';
  createdAt: Timestamp;
};
export type TestContext = {
  deviceType: 'SINGLE_PHASE' | 'THREE_PHASE' | 'UNSPECIFIED';
  productModel?: string | null;
  firmwareVersion?: string | null;
  hardwareVersion?: string | null;
  protocolName?: string | null;
  protocolVersion?: string | null;
};
export type Suite = {
  id: Id;
  projectId: Id;
  parentId: Id | null;
  name: string;
  sourcePath: string;
  revision: Sha256;
  caseCount: number;
};
export type SourceRef = { path: string; line: number; revision: Sha256 };
export type Step = { keyword: string; arguments: Array<string>; line: number };
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
export type SelectionCreate =
  | { mode: 'EXPLICIT'; caseIds: Array<Id> }
  | { mode: 'FILTER'; filter: CaseFilter; excludedCaseIds?: Array<Id> };
export type CaseVersion = { caseId: Id; version: number };
export type Selection = {
  id: Uuid;
  projectId: Id;
  count: number;
  cases: Array<CaseVersion>;
  digest: Sha256;
  expiresAt: Timestamp;
};
export type ImportIssue = {
  code: string;
  sourcePath: string;
  line: number | null;
  message: string;
};
export type ImportJob = {
  id: Uuid;
  projectId: Id;
  kind: 'ROBOT_CATALOG' | 'ROBOT_RESULT';
  status: 'QUEUED' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED';
  createdAt: Timestamp;
  completedAt: Timestamp | null;
  counts: { added: number; updated: number; archived: number; unmapped: number };
  issues: Array<ImportIssue>;
  runId: Id | null;
  revision: Sha256 | null;
};
export type RunCounts = { total: number; pass: number; fail: number; skip: number };
export type Run = {
  id: Id;
  projectId: Id;
  name: string;
  origin: 'IMPORTED' | 'MOCK';
  status: 'COMPLETED' | 'INCOMPLETE';
  startedAt: Timestamp | null;
  finishedAt: Timestamp | null;
  elapsedMs: number | null;
  counts: RunCounts;
  catalogRevision: Sha256;
  testContext: TestContext;
  importJobId: Uuid;
};
export type Result = {
  id: Id;
  runId: Id;
  caseId: Id | null;
  suitePath: string;
  caseName: string;
  status: 'PASS' | 'FAIL' | 'SKIP';
  message: string;
  elapsedMs: number;
  failedKeyword: string | null;
  artifactId: Id;
  sourceLine: number | null;
};
export type Artifact = {
  id: Id;
  runId: Id;
  kind: 'ROBOT_OUTPUT_XML' | 'LOG_TEXT' | 'ROBOT_LOG_HTML' | 'ROBOT_REPORT_HTML';
  name: string;
  sizeBytes: number;
  sha256: Sha256;
};
export type ArtifactLines = {
  artifactId: Id;
  fromLine: number;
  lines: Array<string>;
  hasMore: boolean;
};
export type Document = {
  id: Id;
  knowledgeBaseId: Id;
  title: string;
  versionLabel: string;
  contentHash: Sha256;
  mimeType: 'text/markdown' | 'application/pdf';
  status: 'QUEUED' | 'PROCESSING' | 'READY' | 'FAILED' | 'DELETING' | 'DELETED';
  stage: 'UPLOADING' | 'PARSING' | 'CHUNKING' | 'EMBEDDING' | 'FINISHED' | 'CLEANUP';
  error: Error | null;
  createdAt: Timestamp;
};
export type Conversation = { id: Uuid; projectId: Id; title: string; updatedAt: Timestamp };
export type Overview = {
  project: Project;
  catalogCaseCount: number;
  lastRun: Run | null;
  readyDocumentCount: number;
  failedDocumentCount: number;
  recentRuns: Array<Run>;
  recentConversations: Array<Conversation>;
  attentionDocuments: Array<Document>;
};
export type JobAccepted = { jobId: Uuid; status: 'QUEUED' };
export type ProjectPage = { items: Array<Project>; total: number; page: number; size: number };
export type MemberPage = { items: Array<Member>; total: number; page: number; size: number };
export type SuitePage = { items: Array<Suite>; total: number; page: number; size: number };
export type ImportJobPage = { items: Array<ImportJob>; total: number; page: number; size: number };
export type TestCasePage = { items: Array<TestCase>; total: number; page: number; size: number };
export type RunPage = { items: Array<Run>; total: number; page: number; size: number };
export type ResultPage = { items: Array<Result>; total: number; page: number; size: number };
export type ArtifactPage = { items: Array<Artifact>; total: number; page: number; size: number };
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
  Overview: Overview;
  ImportJob: ImportJob;
  ImportJobPage: ImportJobPage;
  JobAccepted: JobAccepted;
  Run: Run;
  RunPage: RunPage;
  ResultPage: ResultPage;
  ArtifactPage: ArtifactPage;
  ArtifactLines: ArtifactLines;
  MemberPage: MemberPage;
  Member: Member;
  ProjectCreate: ProjectCreate;
  ProjectPatch: ProjectPatch;
  SetMember: SetMember;
  TestContext: TestContext;
};
export function readContract<K extends keyof ContractTypes>(
  name: K,
  value: unknown,
): ContractTypes[K] {
  if (!validators[name]?.(value))
    throw new Error('服务响应或请求格式不符合约定，请重试或联系管理员。');
  // The assertion follows validation against the exact schema that generated these types.
  return value as ContractTypes[K];
}
