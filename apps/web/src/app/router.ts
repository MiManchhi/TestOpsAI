import { createRouter, createWebHistory, type RouterHistory } from 'vue-router';
import LoginPage from '../features/auth/LoginPage.vue';
import { auth, type AuthSession } from '../features/auth/session';
const WorkspaceLayout = () => import('../features/workspace/WorkspaceLayout.vue');
const OverviewPage = () => import('../features/workspace/OverviewPage.vue');
const KnowledgePage = () => import('../features/knowledge/KnowledgePage.vue');
const KnowledgeDetailPage = () => import('../features/knowledge/KnowledgeDetailPage.vue');
const DocumentPage = () => import('../features/knowledge/DocumentPage.vue');
const AssistantPage = () => import('../features/assistant/AssistantPage.vue');
const DraftPage = () => import('../features/drafts/DraftPage.vue');
const TestCasesPage = () => import('../features/test-cases/TestCasesPage.vue');
const CaseDetailPage = () => import('../features/test-cases/CaseDetailPage.vue');
const ProjectPage = () => import('../features/projects/ProjectPage.vue');
const AccountPage = () => import('../features/workspace/AccountPage.vue');
const ImportsPage = () => import('../features/imports/ImportsPage.vue');
const RunsPage = () => import('../features/runs/RunsPage.vue');
const RunDetailPage = () => import('../features/runs/RunDetailPage.vue');

const destinations = [
  '/overview',
  '/knowledge',
  '/test-cases',
  '/runs',
  '/imports',
  '/assistant',
  '/settings/project',
  '/settings/account',
];
export function safeDestination(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//'))
    return '/overview';
  try {
    const url = new URL(value, 'https://testops.invalid');
    if (
      url.origin !== 'https://testops.invalid' ||
      (!destinations.includes(url.pathname) &&
        !/^\/(test-cases|runs)\/[1-9][0-9]{0,18}$/.test(url.pathname) &&
        !/^\/knowledge\/[1-9][0-9]{0,18}(\/documents\/[1-9][0-9]{0,18})?$/.test(url.pathname) &&
        !/^\/(assistant|drafts)\/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(
          url.pathname,
        ))
    )
      return '/overview';
    const projectId = url.searchParams.get('projectId');
    const query = new URLSearchParams();
    if (projectId && /^[1-9][0-9]{0,18}$/.test(projectId)) query.set('projectId', projectId);
    if (url.pathname.startsWith('/assistant'))
      for (const name of ['kbId', 'documentId', 'runId', 'selectionId']) {
        const value = url.searchParams.get(name);
        if (
          value &&
          (name === 'selectionId'
            ? /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i
            : /^[1-9][0-9]{0,18}$/
          ).test(value)
        )
          query.set(name, value);
      }
    const citation = url.searchParams.get('citationId');
    if (
      url.pathname.includes('/documents/') &&
      citation &&
      /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(citation)
    )
      query.set('citationId', citation);
    return url.pathname + (query.size ? '?' + query.toString() : '');
  } catch {
    return '/overview';
  }
}

export function createAppRouter(
  session: AuthSession = auth,
  history: RouterHistory = createWebHistory(),
) {
  const router = createRouter({
    history,
    routes: [
      { path: '/login', component: LoginPage, meta: { title: '登录' } },
      {
        path: '/',
        component: WorkspaceLayout,
        children: [
          { path: '', redirect: '/overview' },
          { path: 'overview', component: OverviewPage, meta: { title: '工作台', project: true } },
          {
            path: 'knowledge',
            component: KnowledgePage,
            meta: { title: '知识库', project: true },
          },
          {
            path: 'knowledge/:kbId',
            component: KnowledgeDetailPage,
            meta: { title: '知识库详情', project: true },
          },
          {
            path: 'knowledge/:kbId/documents/:documentId',
            component: DocumentPage,
            meta: { title: '文档阅读', project: true },
          },
          {
            path: 'assistant/:conversationId?',
            component: AssistantPage,
            meta: { title: 'AI 助手', project: true },
          },
          {
            path: 'drafts/:draftId',
            component: DraftPage,
            meta: { title: '用例建议草稿', project: true },
          },
          {
            path: 'test-cases',
            component: TestCasesPage,
            meta: { title: 'Robot 用例', project: true },
          },
          {
            path: 'test-cases/:caseId',
            component: CaseDetailPage,
            meta: { title: '用例详情', project: true },
          },
          {
            path: 'settings/project',
            component: ProjectPage,
            meta: { title: '项目与成员' },
          },
          { path: 'imports', component: ImportsPage, meta: { title: '数据导入', project: true } },
          { path: 'runs', component: RunsPage, meta: { title: '运行记录', project: true } },
          {
            path: 'runs/:runId',
            component: RunDetailPage,
            meta: { title: '运行详情', project: true },
          },
          { path: 'settings/account', component: AccountPage, meta: { title: '账户' } },
        ],
      },
      { path: '/:pathMatch(.*)*', redirect: '/overview' },
    ],
    scrollBehavior: () => ({ top: 0 }),
  });
  router.beforeEach(async (to) => {
    await session.restore();
    if (session.state.phase === 'anonymous' && to.path !== '/login') {
      return { path: '/login', query: { redirect: safeDestination(to.fullPath) }, replace: true };
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
