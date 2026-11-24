import { createRouter, createWebHistory, type RouterHistory } from 'vue-router';
import { handoffId } from '../features/workspace/chat-handoff';
import LoginPage from '../features/auth/LoginPage.vue';
import { auth, type AuthSession } from '../features/auth/session';
const PlatformAccountsPage = () => import('../features/auth/PlatformAccountsPage.vue');
const WorkspaceProjectsPage = () => import('../features/projects/WorkspaceProjectsPage.vue');
const WorkspaceProjectSettingsPage = () =>
  import('../features/projects/WorkspaceProjectSettingsPage.vue');
const WorkspaceDocumentsPage = () => import('../features/knowledge/WorkspaceDocumentsPage.vue');
const WorkspaceDocumentPage = () => import('../features/knowledge/WorkspaceDocumentPage.vue');
const WorkspaceBasisPage = () => import('../features/knowledge/WorkspaceBasisPage.vue');
const WorkspacePlatformJobsPage = () =>
  import('../features/knowledge/WorkspacePlatformJobsPage.vue');
const PasswordPage = () => import('../features/auth/PasswordPage.vue');
const WorkspaceLayout = () => import('../features/workspace/WorkspaceLayout.vue');
const WorkspaceChatPage = () => import('../features/assistant/WorkspaceChatPage.vue');
const AccountPage = () => import('../features/workspace/AccountPage.vue');
const WorkspaceRunsPage = () => import('../features/runs/WorkspaceRunsPage.vue');

const destinations = [
  '/chat',
  '/settings/account',
  '/projects',
  '/platform/accounts',
  '/platform/projects',
  '/platform/jobs',
  '/account/password',
];
export function safeDestination(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) return '/chat';
  try {
    const url = new URL(value, 'https://testops.invalid');
    if (
      url.origin !== 'https://testops.invalid' ||
      (!destinations.includes(url.pathname) &&
        !/^\/platform\/projects\/[1-9][0-9]{0,18}(?:\/owner)?$/.test(url.pathname) &&
        !/^\/p\/[1-9][0-9]{0,18}\/(?:chat(?:\/[a-f0-9-]{36})?|runs(?:\/(?:new|[1-9][0-9]{0,18}))?|settings(?:\/(?:members|bases|transfer))?|documents(?:\/new|\/[1-9][0-9]{0,18}\/versions\/[1-9][0-9]{0,18})?)$/.test(
          url.pathname,
        ))
    )
      return '/chat';
    const projectId = url.searchParams.get('projectId');
    const query = new URLSearchParams();
    if (projectId && /^[1-9][0-9]{0,18}$/.test(projectId)) query.set('projectId', projectId);
    if (/^\/p\/[1-9][0-9]{0,18}\/chat(?:\/|$)/.test(url.pathname)) {
      const runId = url.searchParams.get('runId');
      if (runId && /^[1-9][0-9]{0,18}$/.test(runId)) query.set('runId', runId);
    }
    if (/^\/p\/[1-9][0-9]{0,18}\/runs\/[1-9][0-9]{0,18}$/.test(url.pathname)) {
      const artifact = url.searchParams.get('artifactId');
      const from = url.searchParams.get('fromLine');
      if (artifact && /^[1-9][0-9]{0,18}$/.test(artifact)) query.set('artifactId', artifact);
      if (from && /^[1-9][0-9]{0,6}$/.test(from)) query.set('fromLine', from);
    }
    if (/^\/p\/[1-9][0-9]{0,18}\/(documents|runs)\//.test(url.pathname)) {
      const back = handoffId(url.searchParams.get('returnConversationId'));
      if (back) query.set('returnConversationId', back);
      if (url.pathname.endsWith('/documents/new')) {
        const file = handoffId(url.searchParams.get('attachmentId'));
        if (file) query.set('attachmentId', file);
      }
      if (url.pathname.endsWith('/runs/new')) {
        const output = handoffId(url.searchParams.get('output'));
        if (output) query.set('output', output);
        for (const log of url.searchParams
          .getAll('logs')
          .map(handoffId)
          .filter(Boolean)
          .slice(0, 10))
          query.append('logs', log);
      }
    }
    const locator = url.searchParams.get('locator');
    if (
      url.pathname.includes('/versions/') &&
      locator &&
      /^(line|page|paragraph) [1-9][0-9]{0,5}( part [1-9][0-9]{0,5})?$/.test(locator)
    )
      query.set('locator', locator);
    const citation = url.searchParams.get('citationId');
    if (
      url.pathname.includes('/documents/') &&
      citation &&
      /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(citation)
    )
      query.set('citationId', citation);
    return url.pathname + (query.size ? '?' + query.toString() : '');
  } catch {
    return '/chat';
  }
}

export function createAppRouter(
  session: AuthSession = auth,
  history: RouterHistory = createWebHistory(),
) {
  const chatRedirect = (to: { query: Record<string, unknown> }) => {
    const id = to.query.projectId;
    return typeof id === 'string' && /^[1-9][0-9]{0,18}$/.test(id) ? '/p/' + id + '/chat' : '/chat';
  };
  const router = createRouter({
    history,
    routes: [
      { path: '/account/password', component: PasswordPage, meta: { title: '修改密码' } },
      { path: '/login', component: LoginPage, meta: { title: '登录' } },
      {
        path: '/',
        component: WorkspaceLayout,
        children: [
          { path: '', redirect: chatRedirect },
          { path: 'chat', component: { template: '<span />' }, meta: { title: 'AI 工作台' } },
          {
            path: 'p/:projectId/chat/:conversationId?',
            component: WorkspaceChatPage,
            meta: { title: 'AI 工作台', chat: true },
          },
          { path: 'p/:projectId/runs', component: WorkspaceRunsPage, meta: { title: '运行记录' } },
          {
            path: 'p/:projectId/runs/new',
            component: WorkspaceRunsPage,
            meta: { title: '登记复测' },
          },
          {
            path: 'p/:projectId/runs/:runId',
            component: WorkspaceRunsPage,
            meta: { title: '运行详情' },
          },
          {
            path: 'p/:projectId/documents',
            component: WorkspaceDocumentsPage,
            meta: { title: '项目资料' },
          },
          {
            path: 'p/:projectId/documents/new',
            component: WorkspaceDocumentsPage,
            meta: { title: '提交资料' },
          },
          {
            path: 'p/:projectId/documents/:documentId/versions/:versionId',
            component: WorkspaceDocumentPage,
            meta: { title: '资料详情' },
          },
          {
            path: 'p/:projectId/settings/bases',
            component: WorkspaceBasisPage,
            meta: { title: '验收依据' },
          },
          {
            path: 'platform/jobs',
            component: WorkspacePlatformJobsPage,
            meta: { title: '处理状态', platform: true },
          },
          {
            path: 'projects',
            component: WorkspaceProjectsPage,
            props: { platform: false },
            meta: { title: '我的项目' },
          },
          {
            path: 'platform/accounts',
            component: PlatformAccountsPage,
            meta: { title: '平台账号', platform: true },
          },
          {
            path: 'platform/projects',
            component: WorkspaceProjectsPage,
            props: { platform: true },
            meta: { title: '平台项目', platform: true },
          },
          {
            path: 'platform/projects/:projectId/:section(owner)?',
            component: WorkspaceProjectSettingsPage,
            props: { platform: true },
            meta: { title: '平台项目详情', platform: true },
          },
          {
            path: 'p/:projectId/settings/:section(members|transfer)?',
            component: WorkspaceProjectSettingsPage,
            props: { platform: false },
            meta: { title: '项目管理' },
          },
          { path: 'settings/account', component: AccountPage, meta: { title: '账户' } },
        ],
      },
      { path: '/:pathMatch(.*)*', redirect: '/chat' },
    ],
    scrollBehavior: () => ({ top: 0 }),
  });
  router.beforeEach(async (to) => {
    await session.restore();
    if (session.state.phase === 'password-change' && to.path !== '/account/password') {
      const destination = to.path === '/login' ? to.query.redirect : to.fullPath;
      return {
        path: '/account/password',
        query: { redirect: safeDestination(destination) },
        replace: true,
      };
    }
    if (session.state.phase === 'anonymous' && to.path !== '/login') {
      return { path: '/login', query: { redirect: safeDestination(to.fullPath) }, replace: true };
    }
    if (
      to.meta.platform &&
      session.state.phase === 'authenticated' &&
      !session.state.user?.roles.includes('ADMIN')
    ) {
      return { path: '/projects', replace: true };
    }
    if (session.state.phase !== 'anonymous' && to.path === '/login')
      return safeDestination(to.query.redirect);
  });
  router.afterEach((to, _from, failure) => {
    if (failure || typeof document === 'undefined') return;
    document.title = `${String(to.meta.title ?? '工作台')} · TestOps AI`;
  });
  return router;
}
