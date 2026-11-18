import { createRouter, createWebHistory, type RouterHistory } from 'vue-router';
import LoginPage from '../features/auth/LoginPage.vue';
import { auth, type AuthSession } from '../features/auth/session';
import WorkspaceLayout from '../features/workspace/WorkspaceLayout.vue';
import OverviewPage from '../features/workspace/OverviewPage.vue';
import ComingSoonPage from '../features/workspace/ComingSoonPage.vue';
import TestCasesPage from '../features/test-cases/TestCasesPage.vue';
import CaseDetailPage from '../features/test-cases/CaseDetailPage.vue';
import ProjectPage from '../features/projects/ProjectPage.vue';
import AccountPage from '../features/workspace/AccountPage.vue';

const destinations = [
  '/overview',
  '/knowledge',
  '/test-cases',
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
        !/^\/test-cases\/[1-9][0-9]{0,18}$/.test(url.pathname))
    )
      return '/overview';
    const projectId = url.searchParams.get('projectId');
    return (
      url.pathname +
      (projectId && /^[1-9][0-9]{0,18}$/.test(projectId) ? `?projectId=${projectId}` : '')
    );
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
            component: ComingSoonPage,
            props: { kind: 'knowledge' },
            meta: { title: '知识库' },
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
            meta: { title: '项目与成员', project: true },
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
