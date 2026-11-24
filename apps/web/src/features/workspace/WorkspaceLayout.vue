<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { errorMessage } from '../../app/http';
import type { WorkspaceProject } from '../../app/workspace-contracts';
import { auth } from '../auth/session';
import AppIcon from '../../app/AppIcon.vue';
import { listWorkspaceProjects } from '../projects/workspace-api';

const route = useRoute();
const router = useRouter();
const memberships = ref<WorkspaceProject[]>([]);
const loading = ref(true);
const error = ref('');
const controller = new AbortController();
const admin = computed(() => auth.state.user?.roles.includes('ADMIN') ?? false);
const current = computed(
  () =>
    memberships.value.find(
      (item) => item.projectId === (route.params.projectId ?? route.query.projectId),
    ) ?? (route.params.projectId ? undefined : memberships.value[0]),
);
const prefix = computed(() => (current.value ? '/p/' + current.value.projectId : ''));
const roleNames = { OWNER: '项目负责人', TEST: '测试工程师', DEV: '开发工程师' } as const;
const role = computed(() =>
  route.meta.platform && admin.value
    ? '平台管理员'
    : current.value?.myRole
      ? roleNames[current.value.myRole]
      : '',
);
const projectMenu = computed(() =>
  prefix.value
    ? [
        { to: prefix.value + '/chat', name: 'AI 工作台', icon: 'chat' as const },
        { to: prefix.value + '/documents', name: '项目资料', icon: 'book' as const },
        { to: prefix.value + '/runs', name: '运行记录', icon: 'activity' as const },
        ...(current.value?.myRole === 'OWNER'
          ? [
              {
                to: prefix.value + '/settings/members',
                name: '项目管理',
                icon: 'settings' as const,
              },
            ]
          : []),
      ]
    : [],
);
const platformMenu = [
  { to: '/platform/projects', name: '平台项目', icon: 'grid' as const },
  { to: '/platform/accounts', name: '平台账号', icon: 'user' as const },
  { to: '/platform/jobs', name: '处理状态', icon: 'activity' as const },
];
const showPlatform = computed(() => admin.value && (route.meta.platform || !current.value));
const chat = computed(() => route.meta.chat && Boolean(route.params.projectId));

async function loadMemberships() {
  try {
    loading.value = true;
    error.value = '';
    const items: WorkspaceProject[] = [];
    for (let page = 0; ; page++) {
      const result = await listWorkspaceProjects(false, page, controller.signal);
      items.push(...result.items);
      if (items.length >= result.total || !result.items.length) break;
    }
    memberships.value = items.filter((item) => item.myRole);
  } catch (cause) {
    if (!controller.signal.aborted) error.value = errorMessage(cause);
  } finally {
    if (!controller.signal.aborted) loading.value = false;
  }
}
onMounted(loadMemberships);
watch([() => route.path, loading], () => {
  if (route.path !== '/chat' || loading.value || error.value) return;
  if (admin.value) void router.replace('/platform/projects');
  else if (prefix.value) void router.replace(prefix.value + '/chat');
});
onBeforeUnmount(() => controller.abort());
function switchProject(event: Event) {
  const id = (event.target as HTMLSelectElement).value;
  if (id) void router.push('/p/' + id + '/chat');
}
function active(path: string) {
  return (
    route.path === path ||
    route.path.startsWith(path + '/') ||
    (path.endsWith('/settings/members') &&
      (route.path === prefix.value + '/settings' ||
        route.path.startsWith(prefix.value + '/settings/')))
  );
}
</script>

<template>
  <div class="app-shell">
    <a class="skip-link" href="#workspace-content">跳转到主要内容</a>
    <aside class="nav-rail">
      <RouterLink to="/chat" class="product-mark" aria-label="TestOps AI 首页"
        ><AppIcon name="activity"
      /></RouterLink>
      <nav aria-label="主导航">
        <RouterLink
          v-for="item in showPlatform ? platformMenu : projectMenu"
          :key="item.to"
          :to="item.to"
          :class="{ active: active(item.to) }"
          :aria-current="active(item.to) ? 'page' : undefined"
          ><AppIcon :name="item.icon" />{{ item.name }}</RouterLink
        >
        <RouterLink v-if="admin && !showPlatform" to="/platform/projects"
          ><AppIcon name="grid" />平台管理</RouterLink
        >
      </nav>
      <RouterLink
        class="profile"
        to="/settings/account"
        :aria-label="'我的账户 · ' + auth.state.user?.username"
        >{{ auth.state.user?.username.slice(0, 1).toUpperCase() }}</RouterLink
      >
    </aside>
    <div class="workspace">
      <header class="app-header">
        <strong class="wordmark">TestOps <span>AI</span></strong
        ><span class="divider"></span>
        <AppIcon name="folder" />
        <select
          v-if="memberships.length"
          aria-label="当前项目"
          :value="showPlatform ? '' : (current?.projectId ?? '')"
          @change="switchProject"
        >
          <option value="" disabled>{{ showPlatform ? '平台管理' : '选择项目' }}</option>
          <option v-for="item in memberships" :key="item.projectId" :value="item.projectId">
            {{ item.name }}
          </option>
        </select>
        <span v-else>{{ showPlatform ? '平台管理' : '我的工作空间' }}</span>
        <RouterLink class="identity" to="/settings/account"
          ><span>{{ auth.state.user?.username }}</span
          ><small>{{ role }}</small></RouterLink
        >
      </header>
      <main
        id="workspace-content"
        :class="chat ? 'chat-content' : 'business-content'"
        tabindex="-1"
      >
        <p v-if="auth.state.storageWarning" role="status">{{ auth.state.storageWarning }}</p>
        <template v-if="route.path === '/chat'">
          <p v-if="loading" role="status">正在读取可访问项目…</p>
          <template v-else-if="error">
            <p role="alert">{{ error }}</p>
            <button class="button button-secondary" @click="loadMemberships">重新读取</button>
          </template>
          <template v-else-if="!current && !admin">
            <h1 data-page-heading tabindex="-1">暂无可访问项目</h1>
            <p>请联系项目负责人将你加入项目。</p>
            <button class="button button-secondary" @click="loadMemberships">重新读取</button>
          </template>
        </template>
        <RouterView v-else v-slot="{ Component }"
          ><component :is="Component" :key="route.meta.chat ? 'chat' : route.path"
        /></RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  height: 100dvh;
  background: var(--canvas);
  overflow: hidden;
}
.nav-rail {
  background: #17273e;
  color: #d3dbeb;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 15px 6px 12px;
  gap: 24px;
}
.product-mark {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  color: white;
  background: #466ce2;
  box-shadow: inset 0 0 0 1px #ffffff40;
}
.product-mark .icon {
  width: 24px;
  height: 24px;
  stroke-width: 2.4;
}
nav {
  width: 100%;
  display: grid;
  gap: 8px;
}
nav a {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  padding: 10px 0;
  font-size: 11px;
  font-weight: 600;
  border-radius: 9px;
}
nav a.active {
  background: #334c73;
  color: white;
}
nav a:hover {
  background: #2a405f;
}
.profile {
  margin-top: auto;
  border: 1px solid #586b84;
  border-radius: 50%;
  height: 34px;
  width: 34px;
  display: grid;
  place-items: center;
  color: white;
  font-weight: 650;
}
.workspace {
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.app-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 clamp(14px, 2.5vw, 36px);
  height: 60px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--border);
  background: white;
}
.wordmark {
  font-size: 19px;
  letter-spacing: -0.8px;
  white-space: nowrap;
}
.wordmark span {
  color: var(--primary);
}
.divider {
  height: 19px;
  border-left: 1px solid var(--border);
  margin-inline: 6px;
}
.app-header > .icon {
  width: 17px;
  color: var(--muted);
}
.app-header select {
  max-width: min(42vw, 320px);
  padding: 4px 20px 4px 0;
  border: 0;
  background: transparent;
  min-height: 36px;
  font-weight: 600;
}
.identity {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.identity small {
  padding: 3px 8px;
  color: #536381;
  border-radius: 5px;
  background: #eff2f8;
}
main {
  overflow: auto;
  flex: 1;
  min-height: 0;
}
.business-content {
  padding: clamp(20px, 3vw, 40px);
  scrollbar-gutter: stable;
}
.business-content > :deep(section),
.business-content > :deep(div) {
  max-width: 1240px;
  margin-inline: auto;
}
.chat-content {
  overflow: hidden;
}
@media (max-width: 740px) {
  .app-shell {
    display: flex;
    flex-direction: column-reverse;
  }
  .workspace {
    flex: 1;
  }
  .nav-rail {
    height: 56px;
    flex-direction: row;
    padding: 2px 5px;
    gap: 0;
    flex-shrink: 0;
  }
  .nav-rail .product-mark,
  .nav-rail .profile {
    display: none;
  }
  nav {
    display: flex;
    justify-content: space-evenly;
    gap: 1px;
  }
  nav a {
    flex: 1;
    gap: 2px;
    padding: 4px 2px;
    font-size: 10px;
  }
  nav a .icon {
    height: 18px;
  }
  .app-header {
    height: 48px;
    gap: 5px;
  }
  .wordmark {
    font-size: 16px;
  }
  .identity small,
  .app-header > .icon {
    display: none;
  }
  .app-header select {
    font-size: 13px;
  }
  .business-content {
    padding: 18px 12px;
  }
}
</style>
