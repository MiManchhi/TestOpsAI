<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppIcon from './AppIcon.vue';
import { safeDestination } from './router';
import { auth } from '../features/auth/session';
const route = useRoute();
const router = useRouter();
watch(
  () => auth.state.phase,
  (phase) => {
    if (phase === 'anonymous' && route.path !== '/login')
      void router.replace({ path: '/login', query: { redirect: safeDestination(route.fullPath) } });
  },
);
function checkExpiry() {
  auth.checkExpiry();
}
function restart() {
  auth.logout();
}
onMounted(() => {
  window.addEventListener('focus', checkExpiry);
  document.addEventListener('visibilitychange', checkExpiry);
});
onBeforeUnmount(() => {
  window.removeEventListener('focus', checkExpiry);
  document.removeEventListener('visibilitychange', checkExpiry);
});
</script>
<template>
  <main v-if="auth.state.phase === 'unavailable'" class="session-page">
    <section class="panel session-card" aria-labelledby="session-heading">
      <span class="icon-surface"><AppIcon name="shield" /></span>
      <h1 id="session-heading" data-page-heading tabindex="-1">暂时无法验证登录</h1>
      <p role="alert">{{ auth.state.error }}</p>
      <small v-if="auth.state.requestId">请求编号：{{ auth.state.requestId }}</small>
      <div class="button-row">
        <button class="button button-primary" @click="auth.retry">重新验证</button
        ><button class="button button-secondary" @click="restart">重新登录</button>
      </div>
    </section>
  </main>
  <main
    v-else-if="auth.state.phase === 'checking' && route.path !== '/login'"
    class="session-page"
    aria-busy="true"
  >
    <div class="loading-state" role="status">
      <span class="spinner" />
      <p>正在验证登录…</p>
    </div>
  </main>
  <RouterView v-else />
</template>
