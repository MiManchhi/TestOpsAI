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

type WorkspaceTypes = {
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
