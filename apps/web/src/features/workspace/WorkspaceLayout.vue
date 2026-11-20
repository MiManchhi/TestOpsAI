<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppIcon from '../../app/AppIcon.vue';
import { auth } from '../auth/session';
import { projects } from '../projects/context';
import { caseCatalog } from '../test-cases/catalog';

const route = useRoute();
const router = useRouter();
const selectedProject = computed({ get: () => projects.state.selectedId, set: projects.select });
const projectQuery = computed(() =>
  projects.state.selectedId ? { projectId: projects.state.selectedId } : {},
);
const menuOpen = ref(false);
const menuButton = ref<HTMLButtonElement>();
const sidebar = ref<HTMLElement>();
const mobileViewport = window.matchMedia('(max-width: 700px)');
function handleViewportChange(event: MediaQueryListEvent) {
  if (!event.matches) menuOpen.value = false;
}
onMounted(() => mobileViewport.addEventListener('change', handleViewportChange));
onMounted(() => {
  void projects.load(route.query.projectId);
});
const platformAdmin = computed(() => auth.state.user?.roles.includes('ADMIN') ?? false);
const navigation = [
  { path: '/overview', label: '工作台', icon: 'grid', enabled: true },
  { path: '/assistant', label: 'AI 助手', icon: 'activity', enabled: true },
  { path: '/knowledge', label: '知识库', icon: 'book', enabled: true },
  { path: '/test-cases', label: 'Robot 用例', icon: 'check', enabled: true },
  { path: '/runs', label: '运行记录', icon: 'activity', enabled: true },
  { path: '/imports', label: '数据导入', icon: 'arrow', enabled: true },
] as const;
watch(
  () => projects.state.selectedId,
  (id, previous) => {
    caseCatalog.setProject(id);
    if (!id || route.meta.platform || route.path === '/projects' || route.path.startsWith('/p/'))
      return;
    const changedProject =
      Boolean(previous) ||
      (typeof route.query.projectId === 'string' && route.query.projectId !== id);
    const path =
      changedProject && route.path.startsWith('/test-cases/')
        ? '/test-cases'
        : changedProject && route.path.startsWith('/runs/')
          ? '/runs'
          : changedProject && route.path.startsWith('/knowledge/')
            ? '/knowledge'
            : changedProject &&
                (route.path.startsWith('/assistant/') || route.path.startsWith('/drafts/'))
              ? '/assistant'
              : route.path;
    if (route.query.projectId !== id || path !== route.path)
      void router.replace({ path, query: { projectId: id } });
  },
);
watch(
  () => route.query.projectId,
  (id) => {
    if (
      projects.state.phase !== 'ready' ||
      typeof id !== 'string' ||
      id === projects.state.selectedId
    )
      return;
    if (projects.state.items.some((project) => project.id === id)) projects.select(id);
    else void projects.load(id);
  },
);

async function setMenu(open: boolean) {
  menuOpen.value = open;
  await nextTick();
  // Wait for visibility styles before focusing a previously hidden drawer.
  await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
  if (menuOpen.value !== open) return;
  if (open) sidebar.value?.querySelector<HTMLElement>('a')?.focus();
  else if (mobileViewport.matches) menuButton.value?.focus();
}
function focusDrawer(event: TransitionEvent) {
  if (menuOpen.value && event.target === sidebar.value && event.propertyName === 'transform') {
    sidebar.value?.querySelector<HTMLElement>('a')?.focus();
  }
}
function trapFocus(event: KeyboardEvent) {
  if (!menuOpen.value || event.key !== 'Tab') return;
  const elements = sidebar.value?.querySelectorAll<HTMLElement>('a[href], button:not(:disabled)');
  const first = elements?.[0];
  const last = elements?.[elements.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last?.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first?.focus();
  }
}
watch(menuOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : '';
});
watch(
  () => route.path,
  () => {
    menuOpen.value = false;
  },
);
function focusHeading() {
  document.querySelector<HTMLElement>('[data-page-heading]')?.focus();
}
onBeforeUnmount(() => {
  document.body.style.overflow = '';
  mobileViewport.removeEventListener('change', handleViewportChange);
  projects.reset();
  caseCatalog.setProject('');
});
function logout() {
  auth.logout('你已退出登录。');
}
</script>

<template>
  <div class="workspace" @keydown.esc="setMenu(false)">
    <a class="skip-link" href="#workspace-content">跳转到主要内容</a>
    <Transition name="veil"
      ><button
        v-if="menuOpen"
        class="sidebar-backdrop"
        aria-label="关闭导航"
        tabindex="-1"
        @click="setMenu(false)"
    /></Transition>
    <aside
      id="workspace-navigation"
      ref="sidebar"
      class="sidebar"
      :class="{ 'is-open': menuOpen }"
      :role="menuOpen ? 'dialog' : undefined"
      :aria-modal="menuOpen ? true : undefined"
      aria-label="工作台导航"
      @keydown="trapFocus"
      @transitionend="focusDrawer"
    >
      <div class="sidebar-brand-row">
        <RouterLink
          :to="{ path: '/overview', query: projectQuery }"
          class="brand"
          @click="setMenu(false)"
          ><span class="brand-mark">t<span>·</span></span
          ><span>TestOps <b>AI</b></span></RouterLink
        ><button class="icon-button mobile-only" aria-label="关闭导航" @click="setMenu(false)">
          <AppIcon name="close" />
        </button>
      </div>
      <p class="sidebar-caption">智能电表测试协作</p>
      <nav aria-label="主导航">
        <RouterLink to="/projects" class="nav-item" @click="setMenu(false)">我的项目</RouterLink>
        <template v-if="platformAdmin">
          <RouterLink to="/platform/projects" class="nav-item" @click="setMenu(false)"
            >平台项目</RouterLink
          >
          <RouterLink to="/platform/accounts" class="nav-item" @click="setMenu(false)"
            >平台账号</RouterLink
          >
        </template>
        <template v-for="item in route.meta.platform ? [] : navigation" :key="item.path">
          <RouterLink
            v-if="item.enabled"
            :to="{ path: item.path, query: projectQuery }"
            class="nav-item"
            :class="{
              'router-link-active':
                (item.path === '/test-cases' && route.path.startsWith('/test-cases/')) ||
                (item.path === '/runs' && route.path.startsWith('/runs/')) ||
                (item.path === '/knowledge' && route.path.startsWith('/knowledge/')) ||
                (item.path === '/assistant' &&
                  (route.path.startsWith('/assistant/') || route.path.startsWith('/drafts/'))),
            }"
            @click="setMenu(false)"
            ><AppIcon :name="item.icon" /><span>{{ item.label }}</span></RouterLink
          >
          <button
            v-else
            class="nav-item nav-unavailable"
            disabled
            :aria-label="`${item.label}，当前不可用`"
          >
            <AppIcon :name="item.icon" /><span>{{ item.label }}</span>
          </button>
        </template>
      </nav>
      <nav class="secondary-navigation" aria-label="设置导航">
        <RouterLink
          :to="{ path: '/settings/project', query: projectQuery }"
          class="nav-item"
          @click="setMenu(false)"
          ><AppIcon name="shield" />项目与成员</RouterLink
        >
        <button class="nav-item nav-unavailable" disabled aria-label="审计记录，当前不可用">
          <AppIcon name="book" />审计记录
        </button>
      </nav>
      <div class="sidebar-footer">
        <RouterLink to="/settings/account" class="nav-item" @click="setMenu(false)"
          ><AppIcon name="user" />账户</RouterLink
        >
      </div>
    </aside>
    <div class="workspace-body" :inert="menuOpen">
      <header class="topbar">
        <div class="topbar-location">
          <button
            ref="menuButton"
            class="icon-button mobile-only"
            aria-label="打开导航"
            :aria-expanded="menuOpen"
            aria-controls="workspace-navigation"
            @click="setMenu(true)"
          >
            <AppIcon name="menu" />
          </button>
          <div class="project-picker">
            <label for="active-project">当前项目</label
            ><select
              id="active-project"
              v-model="selectedProject"
              :disabled="projects.state.phase !== 'ready' || !projects.state.items.length"
            >
              <option v-if="!projects.state.selectedId" value="">
                {{ projects.state.phase === 'loading' ? '正在读取项目…' : '选择项目' }}
              </option>
              <option v-for="project in projects.state.items" :key="project.id" :value="project.id">
                {{ project.name }}
              </option>
            </select>
          </div>
        </div>
        <div class="topbar-account">
          <span class="avatar">{{ auth.state.user?.username.slice(0, 1).toUpperCase() }}</span><span
            class="account-name"
            >{{ auth.state.user?.username }}</span
          ><span class="topbar-separator" /><button class="text-button" @click="logout">
            <AppIcon name="logout" /><span>退出</span>
          </button>
        </div>
      </header>
      <main id="workspace-content" class="workspace-content" tabindex="-1">
        <div v-if="auth.state.storageWarning" class="notice" role="status">
          {{ auth.state.storageWarning }}
        </div>
        <section
          v-if="route.meta.project && projects.state.phase !== 'ready'"
          class="panel context-state"
          :aria-busy="projects.state.phase === 'loading'"
        >
          <template v-if="projects.state.phase === 'error'"
            ><span class="section-kicker">PROJECT / 项目</span>
            <h1 data-page-heading tabindex="-1">无法读取项目</h1>
            <p role="alert">{{ projects.state.error }}</p>
            <small v-if="projects.state.requestId">请求编号：{{ projects.state.requestId }}</small
            ><button class="button button-primary" @click="projects.load(route.query.projectId)">
              重新读取
            </button></template
          >
          <p v-else role="status"><span class="spinner" />正在读取可访问项目…</p>
        </section>
        <section
          v-else-if="route.meta.project && !projects.state.selectedId"
          class="panel context-state"
        >
          <h1 data-page-heading tabindex="-1">
            {{ projects.state.items.length ? '请选择项目' : '暂无可访问项目' }}
          </h1>
          <p>
            {{
              projects.state.items.length
                ? projects.state.error
                : '请联系项目所有者添加成员权限，然后重新读取。'
            }}
          </p>
          <button class="button button-primary" @click="projects.load()">重新读取</button>
        </section>
        <template v-else>
          <p
            v-if="projects.state.error && projects.state.phase === 'ready'"
            class="notice"
            role="status"
          >
            {{ projects.state.error }}
          </p>
          <RouterView v-slot="{ Component, route: childRoute }"
            ><Transition name="page" mode="out-in" appear @after-enter="focusHeading"
              ><component :is="Component" :key="childRoute.path" /></Transition
          ></RouterView>
        </template>
      </main>
      <footer class="workspace-footer">
        <span>TestOps AI</span><span>测试事实 · 知识依据 · AI 协作</span>
      </footer>
    </div>
  </div>
</template>
