<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import AppIcon from '../../app/AppIcon.vue';
import { auth } from '../auth/session';

const route = useRoute();
const menuOpen = ref(false);
const menuButton = ref<HTMLButtonElement>();
const sidebar = ref<HTMLElement>();
const mobileViewport = window.matchMedia('(max-width: 760px)');
function handleViewportChange(event: MediaQueryListEvent) {
  if (!event.matches) menuOpen.value = false;
}
onMounted(() => mobileViewport.addEventListener('change', handleViewportChange));
const navigation = [
  { path: '/overview', label: '概览', icon: 'grid' },
  { path: '/knowledge', label: '知识库', icon: 'book' },
  { path: '/test-cases', label: '测试用例', icon: 'check' },
] as const;

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
  const elements = sidebar.value?.querySelectorAll<HTMLElement>('a, button');
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
        <RouterLink to="/overview" class="brand" @click="setMenu(false)"
          ><span class="brand-mark">t<span>·</span></span
          ><span>TestOps <b>AI</b></span></RouterLink
        ><button class="icon-button mobile-only" aria-label="关闭导航" @click="setMenu(false)">
          <AppIcon name="close" />
        </button>
      </div>
      <p class="sidebar-caption">测试协作空间</p>
      <nav aria-label="主导航">
        <RouterLink
          v-for="item in navigation"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          @click="setMenu(false)"
          ><AppIcon :name="item.icon" /><span>{{ item.label }}</span
          ><span v-if="item.path !== '/overview'" class="nav-soon">待开放</span></RouterLink
        >
      </nav>
      <div class="sidebar-note">
        <span class="sidebar-note-icon"><AppIcon name="book" /></span
        ><strong>好经验，值得被留下。</strong>
        <p>让知识沉淀，让测试协作<br />更进一步。</p>
      </div>
      <div class="sidebar-footer"><span class="small-dot" /><span>TestOps AI 工作台</span></div>
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
            <AppIcon name="menu" /></button
          ><span class="topbar-product">协作空间</span
          ><AppIcon class="breadcrumb-divider" name="chevron" /><span>{{ route.meta.title }}</span>
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
        <RouterView v-slot="{ Component, route: childRoute }"
          ><Transition name="page" mode="out-in" appear @after-enter="focusHeading"
            ><component :is="Component" :key="childRoute.path" /></Transition
        ></RouterView>
      </main>
      <footer class="workspace-footer">
        <span>TestOps AI</span><span>每一次积累，都让下一次测试更从容。</span>
      </footer>
    </div>
  </div>
</template>
