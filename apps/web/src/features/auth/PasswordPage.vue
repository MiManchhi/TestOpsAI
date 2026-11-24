<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { safeDestination } from '../../app/router';
import { AuthError } from './api';
import { auth } from './session';

const router = useRouter();
const route = useRoute();
const current = ref('');
const next = ref('');
const confirmation = ref('');
const error = ref('');
const sending = ref(false);
const confirmationField = ref<HTMLInputElement>();
const controller = new AbortController();
let attemptKey: string | undefined;

onBeforeUnmount(() => controller.abort());

function edited() {
  attemptKey = undefined;
  error.value = '';
}

async function submit() {
  if (sending.value) return;
  if (next.value !== confirmation.value) {
    error.value = '两次填写的新密码不一致。';
    confirmationField.value?.focus();
    return;
  }
  sending.value = true;
  error.value = '';
  attemptKey ??= crypto.randomUUID();
  try {
    const redirect = safeDestination(route.query.redirect);
    await auth.updatePassword(current.value, next.value, attemptKey, controller.signal);
    current.value = next.value = confirmation.value = '';
    await router.replace({ path: '/login', query: { redirect } });
  } catch (failure) {
    if (controller.signal.aborted) return;
    if (failure instanceof AuthError && failure.status === 401) {
      auth.logout('登录已过期，请重新登录。');
      await router.replace('/login');
      return;
    }
    error.value =
      failure instanceof AuthError && failure.status === 400
        ? '请确认当前密码正确，且新密码与当前密码不同。'
        : '密码暂时无法修改，请稍后重试。';
  } finally {
    sending.value = false;
  }
}
</script>

<template>
  <main class="password-screen">
    <form class="detail-panel password-form" @submit.prevent="submit" @input="edited">
      <h1 data-page-heading tabindex="-1">修改密码</h1>
      <p v-if="auth.state.phase === 'password-change'" class="muted">
        首次进入工作台前，请修改临时密码。
      </p>
      <label class="field">
        当前密码
        <input
          v-model="current"
          type="password"
          autocomplete="current-password"
          required
          maxlength="128"
          :disabled="sending"
        />
      </label>
      <label class="field">
        新密码
        <input
          v-model="next"
          type="password"
          autocomplete="new-password"
          required
          maxlength="128"
          :disabled="sending"
        />
      </label>
      <label class="field">
        确认新密码
        <input
          ref="confirmationField"
          v-model="confirmation"
          type="password"
          autocomplete="new-password"
          required
          maxlength="128"
          :disabled="sending"
          :aria-describedby="error ? 'password-error' : undefined"
        />
      </label>
      <p v-if="error" id="password-error" role="alert">{{ error }}</p>
      <button class="button button-primary" :disabled="sending">
        {{ sending ? '正在保存…' : '保存并重新登录' }}
      </button>
      <button
        type="button"
        class="button button-secondary"
        :disabled="sending"
        @click="
          auth.logout();
          router.replace('/login');
        "
      >
        退出登录
      </button>
    </form>
  </main>
</template>

<style scoped>
.password-screen {
  min-height: 100dvh;
  display: grid;
  place-content: center;
  padding: 1rem;
  grid-template-columns: minmax(0, 28rem);
}

.password-form {
  display: grid;
  gap: 0.85rem;
}

.field {
  display: grid;
  gap: 0.35rem;
}

input,
button {
  min-height: 44px;
}
</style>
