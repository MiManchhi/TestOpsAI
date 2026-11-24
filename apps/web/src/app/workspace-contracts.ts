import validators from 'virtual:testops-contracts';

export type WorkspaceAccount = {
  userId: string;
  username: string;
  displayName: string;
  enabled: boolean;
  mustChangePassword: boolean;
  revision: number;
};
export type WorkspaceUser = { userId: string; displayName: string };
export type ProjectRole = 'OWNER' | 'TEST' | 'DEV';
export type WorkspaceProject = {
  projectId: string;
  name: string;
  description: string;
  owner: WorkspaceUser;
  myRole: ProjectRole | null;
  revision: number;
};
export type WorkspaceMember = WorkspaceUser & { role: ProjectRole; revision: number };
export type WorkspaceHandover = {
  handoverId: string;
  projectId: string;
  oldOwnerId: string;
  newOwnerId: string;
  formerRole: 'TEST' | 'DEV';
  status: 'PENDING' | 'APPROVED' | 'RETURNED';
  mode: 'OWNER_REQUEST' | 'ADMIN_PROXY';
  requestedBy: string;
  decidedBy: string | null;
  reason: string;
  revision: number;
  createdAt: string;
};
export type WorkspacePage<T> = { items: T[]; total: number; page: number; size: number };

export type DocumentKind = 'TENDER' | 'SPEC' | 'KEYWORDS';
export type ReviewStatus = 'PENDING' | 'APPROVED' | 'RETURNED';
export type IndexStatus = 'NOT_STARTED' | 'QUEUED' | 'PROCESSING' | 'READY' | 'FAILED';
export type Visibility = 'PROJECT' | 'RESTRICTED';

export type WorkspaceAttachment = {
  attachmentId: string;
  fileName: string;
  sha256: string;
  expiresAt: string;
};

export type WorkspaceDocument = {
  documentId: string;
  versionId: string;
  title: string;
  kind: DocumentKind;
  versionLabel: string;
  reviewStatus: ReviewStatus;
  indexStatus: IndexStatus;
  visibility: Visibility;
  submittedBy: WorkspaceUser;
  revision: number;
  reason: string;
  createdAt: string;
  errorCode: string | null;
};

export type DocumentContent = {
  versionId: string;
  blocks: { locator: string; text: string }[];
  nextCursor: string | null;
};

export type DocumentReaders = { visibility: Visibility; userIds: string[]; revision: number };
export type Basis = {
  basisId: string;
  label: string;
  versionIds: string[];
  effectiveFrom: string;
  effectiveTo: string | null;
  revision: number;
};
export type WorkspaceJob = {
  jobId: string;
  projectId: string;
  operation: 'REVIEW' | 'INDEX' | 'PARSE_RUN';
  stage: string;
  status: string;
  errorCode: string | null;
  updatedAt: string;
};

export type ChatCitation = {
  citationId: string;
  source: { resourceKind: string; resourceId: string; version: string; locator: string };
  label: string;
  excerpt: string;
  projectSource:
    | { kind: 'DOCUMENT'; versionId: string; locator: string }
    | { kind: 'RUN_LOG'; runId: string; artifactId: string; fromLine: number }
    | null;
};
export type ChatConversation = { conversationId: string; title: string; updatedAt: string };
export type ChatTurn = {
  conversationId: string;
  turnId: string;
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
};
export type ChatFinding = {
  basis: 'AT_RUN' | 'CURRENT' | 'DESIGN';
  versionLabel: string;
  expected: string;
  observed: string | null;
  verdict: 'MEETS' | 'DOES_NOT_MEET' | 'INSUFFICIENT' | 'NEEDS_CONFIRMATION';
  citationIds: string[];
};
export type ChatMessage = {
  messageId: string;
  author: 'USER' | 'ASSISTANT';
  text: string;
  answer: {
    summary: string;
    findings: ChatFinding[];
    robotDraft: string | null;
    pending: string[];
    citations: ChatCitation[];
  } | null;
  createdAt: string;
  attachments: WorkspaceAttachment[];
  turnId: string;
};

export type WorkspaceRun = {
  runId: string;
  name: string;
  executedAt: string;
  providedBy: WorkspaceUser;
  parseStatus: 'QUEUED' | 'PROCESSING' | 'READY' | 'FAILED';
  outputObjectId: string;
  total: number;
  pass: number;
  fail: number;
  skip: number;
  revision: number;
  errorCode: string | null;
};
export type RunResult = {
  resultId: string;
  caseName: string;
  suite: string;
  robotStatus: 'PASS' | 'FAIL' | 'SKIP';
  artifactId: string;
};
export type RunArtifact = {
  artifactId: string;
  name: string;
  kind: 'OUTPUT_XML' | 'LOG_TEXT';
  sha256: string;
};
export type RunLogLine = { line: number; text: string };

export type ChatEvent = {
  turnId: string;
  sequence: number;
  event: 'turn.started' | 'turn.stage' | 'turn.completed' | 'turn.failed' | 'turn.cancelled';
  occurredAt: string;
  data: { error?: { code: string; message: string; requestId: string } };
};
type WorkspaceTypes = {
  Event: ChatEvent;
  Run: WorkspaceRun;
  RunPage: WorkspacePage<WorkspaceRun>;
  ResultPage: WorkspacePage<RunResult>;
  ArtifactPage: WorkspacePage<RunArtifact>;
  LogLinePage: WorkspacePage<RunLogLine>;
  Turn: ChatTurn;
  ConversationPage: WorkspacePage<ChatConversation>;
  MessagePage: WorkspacePage<ChatMessage>;
  Citation: ChatCitation;
  Attachment: WorkspaceAttachment;
  Doc: WorkspaceDocument;
  DocPage: WorkspacePage<WorkspaceDocument>;
  Content: DocumentContent;
  Readers: DocumentReaders;
  Basis: Basis;
  BasisPage: WorkspacePage<Basis>;
  Receipt: { id: string; state: 'ACCEPTED' | 'PROCESSING' | 'READY' | 'FAILED' };
  JobPage: WorkspacePage<WorkspaceJob>;
  Account: WorkspaceAccount;
  AccountPage: WorkspacePage<WorkspaceAccount>;
  Project: WorkspaceProject;
  ProjectPage: WorkspacePage<WorkspaceProject>;
  UserPage: WorkspacePage<WorkspaceUser>;
  Membership: WorkspaceMember;
  MembershipPage: WorkspacePage<WorkspaceMember>;
  Handover: WorkspaceHandover;
  HandoverPage: WorkspacePage<WorkspaceHandover>;
};

export function readWorkspaceContract<K extends keyof WorkspaceTypes>(
  name: K,
  value: unknown,
): WorkspaceTypes[K] {
  if (!validators[`Workspace${name}`]?.(value)) {
    throw new Error('服务响应不符合工作台契约，请重试或联系管理员。');
  }
  return value as WorkspaceTypes[K];
}
