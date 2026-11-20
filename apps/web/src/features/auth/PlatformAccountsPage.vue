<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { ApiError, errorMessage } from '../../app/http';
import type { WorkspaceAccount } from '../../app/workspace-contracts';
import {
  createAccount,
  listAccounts,
  resetAccountPassword,
  setAccountStatus,
} from './platform-api';

const controller = new AbortController();
const items = ref<WorkspaceAccount[]>([]);
const page = ref(0);
const total = ref(0);
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const notice = ref('');
const form = ref<'create' | 'reset' | 'status' | null>(null);
const target = ref<WorkspaceAccount | null>(null);
const username = ref('');
const displayName = ref('');
const temporaryPassword = ref('');
let key = '';

onMounted(() => void load(0));
onBeforeUnmount(() => controller.abort());

async function load(next: number) {
  if (loading.value) return;
  loading.value = true;
  try {
    const result = await listAccounts(next, controller.signal);
    items.value = result.items;
    total.value = result.total;
    page.value = result.page;
  } catch (failure) {
    if (!controller.signal.aborted) error.value = errorMessage(failure);
  } finally {
    loading.value = false;
  }
}

function select(kind: typeof form.value, account: WorkspaceAccount | null = null) {
  form.value = kind;
  target.value = account;
  username.value = displayName.value = temporaryPassword.value = '';
  error.value = notice.value = key = '';
}

async function save() {
  if (busy.value) return;
  key ||= crypto.randomUUID();
  busy.value = true;
  error.value = '';
  try {
    if (form.value === 'create') {
      await createAccount(
        {
          username: username.value.trim(),
          displayName: displayName.value.trim(),
          temporaryPassword: temporaryPassword.value,
        },
        key,
        controller.signal,
      );
    } else if (form.value === 'reset' && target.value) {
      await resetAccountPassword(
        target.value.userId,
        { temporaryPassword: temporaryPassword.value, revision: target.value.revision },
        key,
        controller.signal,
      );
    } else if (form.value === 'status' && target.value) {
      await setAccountStatus(
        target.value.userId,
        { enabled: !target.value.enabled, revision: target.value.revision },
        key,
        controller.signal,
      );
    } else return;
    select(null);
    notice.value = '账户已更新。';
    await load(page.value);
  } catch (failure) {
    if (controller.signal.aborted) return;
    error.value =
      failure instanceof ApiError && failure.code === 'OWNER_TRANSFER_REQUIRED'
        ? '此人仍负责项目。请先完成负责人交接。'
        : errorMessage(failure);
    if (failure instanceof ApiError && failure.code === 'VERSION_CONFLICT') {
      const id = target.value?.userId;
      await load(page.value);
      target.value = items.value.find((item) => item.userId === id) ?? null;
      if (!target.value) form.value = null;
      key = '';
    }
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <section>
    <div class="page-heading-row">
      <h1 data-page-heading tabindex="-1">平台账号</h1>
      <button class="button button-primary" :disabled="busy" @click="select('create')">
        创建账号
      </button>
    </div>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <form v-if="form" class="detail-panel account-form" @submit.prevent="save" @input="key = ''">
      <h2>
        {{
          form === 'create' ? '新建普通账号' : form === 'reset' ? '重置临时密码' : '调整账号状态'
        }}
      </h2>
      <label v-if="form === 'create'"
        >登录名 <input v-model="username" required maxlength="160" :disabled="busy"
      /></label>
      <label v-if="form === 'create'"
        >显示名 <input v-model="displayName" required maxlength="160" :disabled="busy"
      /></label>
      <p v-if="target">{{ target.displayName }} · {{ target.username }}</p>
      <label v-if="form !== 'status'">
        临时密码
        <input
          v-model="temporaryPassword"
          type="password"
          autocomplete="new-password"
          required
          maxlength="128"
          :disabled="busy"
        />
      </label>
      <p v-if="form === 'reset' || (form === 'status' && target?.enabled)" class="muted">
        确认后，该账号现有登录会话失效。
      </p>
      <div class="button-row">
        <button class="button button-primary" :disabled="busy">
          {{ busy ? '正在保存…' : '确认' }}
        </button>
        <button
          type="button"
          class="button button-secondary"
          :disabled="busy"
          @click="select(null)"
        >
          取消
        </button>
      </div>
    </form>
    <section class="detail-panel" :aria-busy="loading">
      <p v-if="!items.length">{{ loading ? '正在读取账号…' : '暂无普通账号' }}</p>
      <ul v-else class="account-list">
        <li v-for="account in items" :key="account.userId">
          <div>
            <strong>{{ account.displayName }}</strong>
            <span class="muted"
              >{{ account.username }} ·
              {{
                !account.enabled ? '已停用' : account.mustChangePassword ? '待改密' : '已启用'
              }}</span
            >
          </div>
          <div class="button-row">
            <button
              class="button button-secondary"
              :disabled="busy"
              @click="select('reset', account)"
            >
              重置密码
            </button>
            <button
              class="button button-secondary"
              :disabled="busy"
              @click="select('status', account)"
            >
              {{ account.enabled ? '停用' : '启用' }}
            </button>
          </div>
        </li>
      </ul>
      <div class="button-row">
        <button
          class="button button-secondary"
          :disabled="loading || page === 0"
          @click="load(page - 1)"
        >
          上一页
        </button>
        <button
          class="button button-secondary"
          :disabled="loading || (page + 1) * 20 >= total"
          @click="load(page + 1)"
        >
          下一页
        </button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.account-form,
.account-list {
  display: grid;
  gap: 0.75rem;
}

.account-form {
  margin-bottom: 1rem;
}

.account-form label,
.account-list li > div:first-child {
  display: grid;
  overflow-wrap: anywhere;
}

.account-list {
  list-style: none;
  padding: 0;
}

.account-list li,
.button-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.account-list li {
  justify-content: space-between;
  padding: 0.5rem 0;
}

button,
input {
  min-height: 44px;
}
</style>
