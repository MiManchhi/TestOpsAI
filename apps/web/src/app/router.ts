import { createRouter, createWebHistory, type RouterHistory } from 'vue-router';
import LoginPage from '../features/auth/LoginPage.vue';
import { auth, type AuthSession } from '../features/auth/session';
import WorkspaceLayout from '../features/workspace/WorkspaceLayout.vue';
import OverviewPage from '../features/workspace/OverviewPage.vue';
import ComingSoonPage from '../features/workspace/ComingSoonPage.vue';

const destinations = ['/overview', '/knowledge', '/test-cases'];
export function safeDestination(value: unknown): string {
  return typeof value === 'string' && destinations.includes(value) ? value : '/overview';
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
          { path: 'overview', component: OverviewPage, meta: { title: '工作台概览' } },
          {
            path: 'knowledge',
            component: ComingSoonPage,
            props: { kind: 'knowledge' },
            meta: { title: '知识库' },
          },
          {
            path: 'test-cases',
            component: ComingSoonPage,
            props: { kind: 'test-cases' },
            meta: { title: '测试用例' },
          },
        ],
      },
      { path: '/:pathMatch(.*)*', redirect: '/overview' },
    ],
    scrollBehavior: () => ({ top: 0 }),
  });
  router.beforeEach(async (to) => {
    await session.restore();
    if (session.state.phase === 'anonymous' && to.path !== '/login') {
      return { path: '/login', query: { redirect: safeDestination(to.path) }, replace: true };
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
