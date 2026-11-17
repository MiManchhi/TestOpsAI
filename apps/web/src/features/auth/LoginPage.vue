<script setup lang="ts">
import { nextTick, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppIcon from '../../app/AppIcon.vue';
import { safeDestination } from '../../app/router';
import { AuthError } from './api';
import { auth } from './session';

const route = useRoute();
const router = useRouter();
const username = ref('');
const password = ref('');
const visible = ref(false);
const pending = ref(false);
const submitted = ref(false);
const error = ref('');
const requestId = ref('');
const errorBox = ref<HTMLElement>();

async function submit() {
  if (pending.value) return;
  submitted.value = true;
  error.value = '';
  requestId.value = '';
  if (!username.value.trim() || !password.value) {
    await nextTick();
    document.getElementById(!username.value.trim() ? 'username' : 'password')?.focus();
    return;
  }
  pending.value = true;
  try {
    if (await auth.signIn(username.value.trim(), password.value)) {
      password.value = '';
      await router.replace(safeDestination(route.query.redirect));
    }
  } catch (cause) {
    error.value = cause instanceof AuthError ? cause.message : '登录未完成，请稍后重试。';
    requestId.value = cause instanceof AuthError ? cause.requestId : '';
    await nextTick();
    errorBox.value?.focus();
  } finally {
    pending.value = false;
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-story" aria-label="TestOps AI 测试协作工作台">
      <a class="brand" href="/login" aria-label="TestOps AI 登录页"
        ><span class="brand-mark">t<span>·</span></span
        ><span>TestOps <b>AI</b></span></a
      >
      <div class="story-content">
        <p class="eyebrow"><span class="tiny-line" /> 测试知识与协作工作台</p>
        <h1>让测试经验，<br />成为团队的<span class="accent-text">共同资产。</span></h1>
        <p class="story-description">从知识积累到测试协作，<br />为每一次可靠交付，打好基础。</p>
        <div class="story-diagram" aria-hidden="true">
          <div class="diagram-node"><AppIcon name="book" /><span>知识沉淀</span></div>
          <span class="diagram-connector" />
          <div class="diagram-center"><AppIcon name="check" /></div>
          <span class="diagram-connector" />
          <div class="diagram-node"><AppIcon name="user" /><span>团队协作</span></div>
        </div>
      </div>
      <p class="story-footer"><span class="small-dot" /> 让知识有序，让协作清晰</p>
    </section>
    <section class="login-panel" aria-labelledby="login-heading">
      <div class="login-card">
        <span class="section-kicker">你的测试工作，从这里开始</span>
        <h2 id="login-heading" data-page-heading tabindex="-1">欢迎回来</h2>
        <p class="muted login-intro">登录账户，进入你的协作空间。</p>
        <div v-if="auth.state.notice" class="notice" role="status">{{ auth.state.notice }}</div>
        <form novalidate :aria-busy="pending" @submit.prevent="submit">
          <div v-if="error" ref="errorBox" class="notice notice-error" role="alert" tabindex="-1">
            <strong>登录未完成</strong>
            <p>{{ error }}</p>
            <small v-if="requestId">请求编号：{{ requestId }}</small>
          </div>
          <div class="form-field">
            <label for="username">用户名</label>
            <input
              id="username"
              v-model="username"
              name="username"
              autocomplete="username"
              required
              placeholder="请输入用户名"
              :disabled="pending"
              :aria-invalid="submitted && !username.trim()"
              :aria-describedby="submitted && !username.trim() ? 'username-error' : undefined"
            />
            <p v-if="submitted && !username.trim()" id="username-error" class="field-error">
              请输入用户名。
            </p>
          </div>
          <div class="form-field">
            <label for="password">密码</label>
            <div class="password-field">
              <input
                id="password"
                v-model="password"
                name="password"
                :type="visible ? 'text' : 'password'"
                autocomplete="current-password"
                required
                placeholder="请输入密码"
                :disabled="pending"
                :aria-invalid="submitted && !password"
                :aria-describedby="submitted && !password ? 'password-error' : undefined"
              /><button
                type="button"
                class="icon-button password-toggle"
                :aria-label="visible ? '隐藏密码' : '显示密码'"
                :aria-pressed="visible"
                @click="visible = !visible"
              >
                <AppIcon :name="visible ? 'eye-off' : 'eye'" />
              </button>
            </div>
            <p v-if="submitted && !password" id="password-error" class="field-error">
              请输入密码。
            </p>
          </div>
          <p class="session-hint"><AppIcon name="shield" /> 在当前标签页保持登录</p>
          <button class="button button-primary login-submit" type="submit" :disabled="pending">
            <span v-if="pending" class="spinner" /><span>{{
              pending ? '正在登录…' : '登录工作台'
            }}</span
            ><AppIcon v-if="!pending" name="arrow" />
          </button>
          <p class="form-footnote">需要账户或登录帮助？请联系管理员。</p>
        </form>
        <div class="login-bottom">
          <span class="mini-brand">TestOps AI</span><span>专注测试 · 有序协作</span>
        </div>
      </div>
    </section>
  </main>
</template>
