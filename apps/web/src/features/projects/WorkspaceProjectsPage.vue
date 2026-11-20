<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { errorMessage } from '../../app/http';
import type { WorkspaceProject, WorkspaceUser } from '../../app/workspace-contracts';
import { createWorkspaceProject, listPlatformUsers, listWorkspaceProjects } from './workspace-api';

const props = defineProps<{ platform: boolean }>();
const controller = new AbortController();
const projects = ref<WorkspaceProject[]>([]);
const page = ref(0);
const total = ref(0);
const loading = ref(false);
const creating = ref(false);
const saving = ref(false);
const error = ref('');
const name = ref('');
const description = ref('');
const owner = ref<WorkspaceUser | null>(null);
const search = ref('');
const users = ref<WorkspaceUser[]>([]);
const searchPage = ref(0);
const searchTotal = ref(0);
const requestedSearch = ref('');
let searchController: AbortController | undefined;
let key = '';

onMounted(() => void load(0));
onBeforeUnmount(() => {
  controller.abort();
  searchController?.abort();
});

async function load(next: number) {
  if (loading.value) return;
  loading.value = true;
  try {
    const result = await listWorkspaceProjects(props.platform, next, controller.signal);
    projects.value = result.items;
    total.value = result.total;
    page.value = result.page;
  } catch (failure) {
    if (!controller.signal.aborted) error.value = errorMessage(failure);
  } finally {
    loading.value = false;
  }
}

async function openForm() {
  creating.value = true;
  error.value = '';
  await findUsers(0);
}

async function findUsers(next: number, q = search.value) {
  searchController?.abort();
  const attempt = new AbortController();
  searchController = attempt;
  try {
    const result = await listPlatformUsers(next, q.trim(), attempt.signal);
    if (attempt.signal.aborted) return;
    users.value = result.items;
    searchPage.value = result.page;
    searchTotal.value = result.total;
    requestedSearch.value = q;
  } catch (failure) {
    if (!attempt.signal.aborted) error.value = errorMessage(failure);
  }
}

function resetForm() {
  searchController?.abort();
  creating.value = false;
  owner.value = null;
  name.value = description.value = search.value = key = '';
  users.value = [];
}

async function submit() {
  if (saving.value) return;
  if (!owner.value) {
    error.value = '请选择一位项目负责人。';
    return;
  }
  error.value = '';
  saving.value = true;
  key ||= crypto.randomUUID();
  try {
    await createWorkspaceProject(
      { name: name.value.trim(), description: description.value, ownerId: owner.value.userId },
      key,
      controller.signal,
    );
    resetForm();
    await load(0);
  } catch (failure) {
    if (!controller.signal.aborted) error.value = errorMessage(failure);
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <section>
    <div class="page-heading-row">
      <h1 data-page-heading tabindex="-1">{{ platform ? '平台项目' : '我的项目' }}</h1>
      <button v-if="platform && !creating" class="button button-primary" @click="openForm">
        创建项目
      </button>
    </div>
    <p v-if="error" role="alert">{{ error }}</p>
    <section v-if="creating" class="detail-panel create-panel">
      <h2>创建项目</h2>
      <form
        id="workspace-project-create"
        class="create-fields"
        @submit.prevent="submit"
        @input="key = ''"
      >
        <label>项目名称 <input v-model="name" required maxlength="160" :disabled="saving" /></label>
        <label>说明 <textarea v-model="description" rows="3" :disabled="saving" /></label>
      </form>
      <form class="person-search" @submit.prevent="findUsers(0)">
        <label>搜索负责人 <input v-model="search" type="search" :disabled="saving" /></label>
        <button class="button button-secondary" :disabled="saving">搜索</button>
      </form>
      <fieldset :disabled="saving">
        <legend>首位项目负责人</legend>
        <p v-if="owner">已选 {{ owner.displayName }}</p>
        <label v-for="person in users" :key="person.userId" class="person-option">
          <input
            type="radio"
            name="initial-owner"
            :checked="owner?.userId === person.userId"
            @change="
              owner = person;
              key = '';
            "
          />
          {{ person.displayName }} · {{ person.userId }}
        </label>
        <p v-if="!users.length" class="muted">没有符合条件的启用账号。</p>
        <div class="button-row">
          <button
            type="button"
            class="button button-secondary"
            :disabled="searchPage === 0"
            @click="findUsers(searchPage - 1, requestedSearch)"
          >
            上一页
          </button>
          <button
            type="button"
            class="button button-secondary"
            :disabled="(searchPage + 1) * 20 >= searchTotal"
            @click="findUsers(searchPage + 1, requestedSearch)"
          >
            下一页
          </button>
        </div>
      </fieldset>
      <div class="button-row">
        <button
          type="submit"
          form="workspace-project-create"
          class="button button-primary"
          :disabled="saving"
        >
          {{ saving ? '正在创建…' : '创建并指派' }}
        </button>
        <button type="button" class="button button-secondary" :disabled="saving" @click="resetForm">
          取消
        </button>
      </div>
    </section>
    <section class="detail-panel" :aria-busy="loading">
      <p v-if="!projects.length">{{ loading ? '正在读取项目…' : '暂无可访问项目' }}</p>
      <ul v-else class="project-list">
        <li v-for="item in projects" :key="item.projectId">
          <div>
            <strong>{{ item.name }}</strong>
            <p class="muted">负责人 {{ item.owner.displayName }}</p>
          </div>
          <RouterLink
            v-if="platform || item.myRole === 'OWNER'"
            class="button button-secondary"
            :to="
              platform
                ? `/platform/projects/${item.projectId}`
                : `/p/${item.projectId}/settings/members`
            "
            >{{ platform ? '查看管理信息' : '管理成员' }}</RouterLink
          >
          <RouterLink
            v-else
            class="button button-secondary"
            :to="{ path: '/assistant', query: { projectId: item.projectId } }"
            >打开工作台</RouterLink
          >
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
.create-panel,
.create-fields,
.project-list {
  display: grid;
  gap: 0.8rem;
}

.create-panel {
  margin-bottom: 1rem;
}

.create-fields label,
.person-search label {
  display: grid;
  gap: 0.3rem;
}

.project-list {
  list-style: none;
  padding: 0;
}

.project-list li,
.button-row,
.person-search,
.person-option {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}

.project-list li {
  justify-content: space-between;
}

button,
input:not([type='radio']),
.person-option {
  min-height: 44px;
}
</style>
