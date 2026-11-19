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
export type CaseCreate = {
  title: string;
  module: string;
  tags?: Array<string>;
  priority: 'P0' | 'P1' | 'P2' | 'P3';
  documentation?: string;
  expectedResult: string;
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
export type KnowledgeBaseCreate = {
  name: string;
  description?: string;
  category: 'METERING' | 'PROTOCOL' | 'TARIFF_CLOCK' | 'PREPAYMENT' | 'EVENTS' | 'ROBOT_METHOD';
  visibility: 'PROJECT' | 'RESTRICTED';
};
export type KnowledgeBasePatch = {
  name?: string;
  description?: string;
  category?: 'METERING' | 'PROTOCOL' | 'TARIFF_CLOCK' | 'PREPAYMENT' | 'EVENTS' | 'ROBOT_METHOD';
  visibility?: 'PROJECT' | 'RESTRICTED';
};
export type KnowledgeBase = {
  id: Id;
  projectId: Id;
  name: string;
  description: string;
  category: 'METERING' | 'PROTOCOL' | 'TARIFF_CLOCK' | 'PREPAYMENT' | 'EVENTS' | 'ROBOT_METHOD';
  visibility: 'PROJECT' | 'RESTRICTED';
  documentCount: number;
  readyCount: number;
  canWrite: boolean;
  updatedAt: Timestamp;
};
export type KnowledgeGrant = { userId: Id; access: 'READ' | 'WRITE' };
export type SetKnowledgeGrant = { access: 'READ' | 'WRITE' };
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
export type DocumentBlock = {
  id: string;
  heading: string;
  text: string;
  page: number | null;
  startLine: number | null;
};
export type DocumentContent = {
  document: Document;
  blocks: Array<DocumentBlock>;
  nextCursor: string | null;
};
export type Citation = {
  id: Uuid;
  kind: 'DOCUMENT' | 'TEST_CASE' | 'TEST_RESULT';
  resourceId: Id;
  title: string;
  version: string;
  locator: { page?: number | null; line?: number | null; section?: string | null };
  excerpt: string;
};
export type ConversationCreate = { projectId: Id; title?: string };
export type Conversation = { id: Uuid; projectId: Id; title: string; updatedAt: Timestamp };
export type MessageContext = {
  testContext: TestContext;
  knowledgeBaseIds: Array<Id>;
  documentIds: Array<Id>;
  selectionId: Uuid | null;
  runId: Id | null;
  knowledgeMode: 'SELECTED' | 'NONE';
};
export type MessageCreate = { clientMessageId: Uuid; text: string; context: MessageContext };
export type Answer = {
  summary: string;
  bodyMarkdown: string;
  evidenceStatus: 'SUFFICIENT' | 'PARTIAL' | 'NONE';
  citationIds: Array<Uuid>;
  caseIds: Array<Id>;
  resultIds: Array<Id>;
  draftId: Uuid | null;
  warnings: Array<string>;
};
export type Message = {
  id: Uuid;
  conversationId: Uuid;
  role: 'USER' | 'ASSISTANT';
  text: string;
  context: MessageContext | null;
  answer: Answer | null;
  turnId: Uuid | null;
  createdAt: Timestamp;
};
export type Turn = {
  id: Uuid;
  conversationId: Uuid;
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  lastSequence: number;
  requestId: Uuid;
};
export type DraftItem = { id: Uuid; values: CaseCreate; citationIds: Array<Uuid> };
export type Draft = {
  id: Uuid;
  projectId: Id;
  conversationId: Uuid;
  revision: number;
  status: 'EDITABLE' | 'COMMITTED' | 'DISCARDED' | 'EXPIRED';
  items: Array<DraftItem>;
  payloadHash: Sha256;
  expiresAt: Timestamp;
  createdCaseIds: Array<Id>;
};
export type DraftPatch = { revision: number; items: Array<DraftItem> };
export type DraftCommit = { revision: number; payloadHash: Sha256; selectedItemIds: Array<Uuid> };
export type DraftCommitResult = { draftId: Uuid; createdCaseIds: Array<Id>; status: 'COMMITTED' };
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
export type StageData = {
  stage: 'RETRIEVING' | 'QUERYING' | 'DRAFTING' | 'RESPONDING';
  label: string;
};
export type ToolStartData = {
  toolCallId: Uuid;
  tool:
    | 'search_test_cases'
    | 'get_test_case_detail'
    | 'get_run_summary'
    | 'get_failed_cases'
    | 'search_knowledge'
    | 'create_case_draft';
};
export type ToolEndData = { toolCallId: Uuid; status: 'SUCCEEDED' | 'FAILED'; summary: string };
export type DeltaData = { text: string };
export type DoneData = { messageId: Uuid; answer: Answer };
export type CancelledData = { reason: 'USER_REQUEST' | 'CLIENT_DISCONNECT' | 'AUTH_REVOKED' };
export type ErrorData = { code: string; message: string; retryable: boolean };
export type TurnStartedEvent = {
  event: 'turn.started';
  turnId: Uuid;
  sequence: number;
  occurredAt: Timestamp;
  data: Turn;
};
export type StageChangedEvent = {
  event: 'stage.changed';
  turnId: Uuid;
  sequence: number;
  occurredAt: Timestamp;
  data: StageData;
};
export type ToolStartedEvent = {
  event: 'tool.started';
  turnId: Uuid;
  sequence: number;
  occurredAt: Timestamp;
  data: ToolStartData;
};
export type ToolFinishedEvent = {
  event: 'tool.finished';
  turnId: Uuid;
  sequence: number;
  occurredAt: Timestamp;
  data: ToolEndData;
};
export type AnswerDeltaEvent = {
  event: 'answer.delta';
  turnId: Uuid;
  sequence: number;
  occurredAt: Timestamp;
  data: DeltaData;
};
export type CitationAvailableEvent = {
  event: 'citation.available';
  turnId: Uuid;
  sequence: number;
  occurredAt: Timestamp;
  data: Citation;
};
export type DraftAvailableEvent = {
  event: 'draft.available';
  turnId: Uuid;
  sequence: number;
  occurredAt: Timestamp;
  data: Draft;
};
export type TurnCompletedEvent = {
  event: 'turn.completed';
  turnId: Uuid;
  sequence: number;
  occurredAt: Timestamp;
  data: DoneData;
};
export type TurnFailedEvent = {
  event: 'turn.failed';
  turnId: Uuid;
  sequence: number;
  occurredAt: Timestamp;
  data: ErrorData;
};
export type TurnCancelledEvent = {
  event: 'turn.cancelled';
  turnId: Uuid;
  sequence: number;
  occurredAt: Timestamp;
  data: CancelledData;
};
export type TurnEvent =
  | TurnStartedEvent
  | StageChangedEvent
  | ToolStartedEvent
  | ToolFinishedEvent
  | AnswerDeltaEvent
  | CitationAvailableEvent
  | DraftAvailableEvent
  | TurnCompletedEvent
  | TurnFailedEvent
  | TurnCancelledEvent;
export type ProjectPage = { items: Array<Project>; total: number; page: number; size: number };
export type MemberPage = { items: Array<Member>; total: number; page: number; size: number };
export type SuitePage = { items: Array<Suite>; total: number; page: number; size: number };
export type ImportJobPage = { items: Array<ImportJob>; total: number; page: number; size: number };
export type TestCasePage = { items: Array<TestCase>; total: number; page: number; size: number };
export type RunPage = { items: Array<Run>; total: number; page: number; size: number };
export type ResultPage = { items: Array<Result>; total: number; page: number; size: number };
export type ArtifactPage = { items: Array<Artifact>; total: number; page: number; size: number };
export type KnowledgeBasePage = {
  items: Array<KnowledgeBase>;
  total: number;
  page: number;
  size: number;
};
export type KnowledgeGrantPage = {
  items: Array<KnowledgeGrant>;
  total: number;
  page: number;
  size: number;
};
export type DocumentPage = { items: Array<Document>; total: number; page: number; size: number };
export type ConversationPage = {
  items: Array<Conversation>;
  total: number;
  page: number;
  size: number;
};
export type MessagePage = { items: Array<Message>; total: number; page: number; size: number };
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
  Uuid: Uuid;
  KnowledgeBaseCreate: KnowledgeBaseCreate;
  KnowledgeBasePatch: KnowledgeBasePatch;
  KnowledgeBase: KnowledgeBase;
  KnowledgeBasePage: KnowledgeBasePage;
  KnowledgeGrant: KnowledgeGrant;
  SetKnowledgeGrant: SetKnowledgeGrant;
  KnowledgeGrantPage: KnowledgeGrantPage;
  Document: Document;
  DocumentPage: DocumentPage;
  DocumentContent: DocumentContent;
  Citation: Citation;
  ConversationCreate: ConversationCreate;
  Conversation: Conversation;
  ConversationPage: ConversationPage;
  MessageCreate: MessageCreate;
  MessageContext: MessageContext;
  Message: Message;
  MessagePage: MessagePage;
  Turn: Turn;
  TurnEvent: TurnEvent;
  Answer: Answer;
  Draft: Draft;
  DraftPatch: DraftPatch;
  DraftCommit: DraftCommit;
  DraftCommitResult: DraftCommitResult;
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
