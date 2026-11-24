<script setup lang="ts">
import { returnToChat } from '../workspace/chat-handoff';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ApiError, errorMessage } from '../../app/http';
import type {
  DocumentContent,
  DocumentReaders,
  Visibility,
  WorkspaceDocument,
  WorkspaceMember,
} from '../../app/workspace-contracts';
import { listWorkspaceMembers, readWorkspaceProject } from '../projects/workspace-api';
import WorkspaceDocumentForm from './WorkspaceDocumentForm.vue';
import {
  downloadOriginal,
  getReaders,
  getVersion,
  listDocumentVersions,
  readVersion,
  retryIndex,
  reviewVersion,
  setReaders,
} from './workspace-api';

const route = useRoute();
const router = useRouter();
const project = computed(() => String(route.params.projectId));
const docId = computed(() => String(route.params.documentId));
const versionId = computed(() => String(route.params.versionId));
const source = computed(() =>
  typeof route.query.locator === 'string' ? route.query.locator : undefined,
);
const detail = ref<WorkspaceDocument | null>(null);
const versions = ref<WorkspaceDocument[]>([]);
const versionPage = ref(0);
const versionTotal = ref(0);
const content = ref<DocumentContent | null>(null);
const owner = ref(false);
const ownerId = ref('');
const error = ref('');
const notice = ref('');
const note = ref('');
const busy = ref(false);
const forming = ref(false);
const reading = ref(false);
const readers = ref<DocumentReaders | null>(null);
const visibility = ref<Visibility>('PROJECT');
const people = ref<WorkspaceMember[]>([]);
const selected = ref<string[]>([]);
const readerPage = ref(0);
const readerTotal = ref(0);
let controller: AbortController | undefined;
let refreshTimer: ReturnType<typeof setTimeout> | undefined;
let command: { fingerprint: string; key: string } | undefined;

const reviews = { PENDING: '待审核', APPROVED: '已批准', RETURNED: '已退回' };
const indexes = {
  NOT_STARTED: '未启动',
  QUEUED: '等待解析',
  PROCESSING: '解析中',
  READY: '已就绪',
  FAILED: '失败',
};
const indexErrors: Record<string, string> = {
  EMBEDDING_PROVIDER_AUTH_FAILED:
    '向量服务拒绝了 API Key，请检查百炼标准 API Key 与 Base URL 是否匹配。',
  EMBEDDING_UNAVAILABLE: '向量服务没有返回有效结果，请检查向量模型配置和连通性。',
};
const href = (id: string) => `/p/${project.value}/documents/${docId.value}/versions/${id}`;

watch(
  [project, docId, versionId, source],
  () => {
    controller?.abort();
    clearTimeout(refreshTimer);
    controller = new AbortController();
    detail.value = content.value = readers.value = null;
    versions.value = [];
    people.value = [];
    owner.value = false;
    ownerId.value = '';
    note.value = error.value = notice.value = '';
    busy.value = forming.value = false;
    command = undefined;
    void load(controller.signal, true);
  },
  { immediate: true },
);

onMounted(() => window.addEventListener('focus', revalidate));
onBeforeUnmount(() => {
  window.removeEventListener('focus', revalidate);
  controller?.abort();
  clearTimeout(refreshTimer);
});

function revalidate() {
  if (controller && !busy.value) void load(controller.signal, false);
}

function handle(failure: unknown, signal: AbortSignal) {
  if (signal.aborted) return;
  if (failure instanceof ApiError && [401, 403, 404].includes(failure.status)) {
    content.value = detail.value = readers.value = null;
    versions.value = [];
  }
  error.value = errorMessage(failure);
}

async function load(signal: AbortSignal, initial: boolean) {
  if (initial) reading.value = true;
  clearTimeout(refreshTimer);
  try {
    const item = await getVersion(project.value, versionId.value, signal);
    if (item.documentId !== docId.value) throw new Error('版本不属于此资料。');
    if (signal.aborted) return;
    const previous = detail.value?.indexStatus;
    detail.value = item;
    if (initial) {
      const context = await readWorkspaceProject(project.value, false, signal);
      owner.value = context.myRole === 'OWNER';
      ownerId.value = context.owner.userId;
      await loadVersions(0);
    }
    if (item.indexStatus === 'READY' && (!content.value || previous !== 'READY')) {
      content.value = await readVersion(
        project.value,
        item.versionId,
        signal,
        source.value ? { locator: source.value } : {},
      );
    }
    if (item.indexStatus === 'QUEUED' || item.indexStatus === 'PROCESSING') {
      refreshTimer = setTimeout(() => {
        if (!signal.aborted) void load(signal, false);
      }, 1200);
    }
  } catch (failure) {
    handle(failure, signal);
  } finally {
    if (!signal.aborted) reading.value = false;
  }
}

async function loadVersions(page: number) {
  const signal = controller?.signal;
  if (!signal) return;
  try {
    const result = await listDocumentVersions(project.value, docId.value, page, signal);
    versions.value = result.items;
    versionPage.value = result.page;
    versionTotal.value = result.total;
  } catch (failure) {
    handle(failure, signal);
  }
}

async function moreContent() {
  const before = content.value;
  const signal = controller?.signal;
  if (!before?.nextCursor || !signal || !detail.value) return;
  try {
    const result = await readVersion(project.value, versionId.value, signal, {
      cursor: before.nextCursor,
    });
    if (content.value === before)
      content.value = { ...result, blocks: [...before.blocks, ...result.blocks] };
  } catch (failure) {
    handle(failure, signal);
  }
}

async function original() {
  const signal = controller?.signal;
  if (!signal || busy.value) return;
  try {
    const item = await downloadOriginal(project.value, versionId.value, signal);
    const url = URL.createObjectURL(item.blob);
    const anchor = window.document.createElement('a');
    anchor.href = url;
    anchor.download = item.filename;
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (failure) {
    handle(failure, signal);
  }
}

async function mutate(
  scope: unknown,
  action: (key: string, signal: AbortSignal) => Promise<unknown>,
  result: string,
) {
  const signal = controller?.signal;
  if (!signal || busy.value || !detail.value) return;
  const fingerprint = JSON.stringify(scope);
  if (command?.fingerprint !== fingerprint) command = { fingerprint, key: crypto.randomUUID() };
  error.value = notice.value = '';
  busy.value = true;
  try {
    await action(command.key, signal);
    if (signal.aborted) return;
    command = undefined;
    notice.value = result;
    await load(signal, false);
    await loadVersions(versionPage.value);
  } catch (failure) {
    handle(failure, signal);
    if (failure instanceof ApiError && failure.status === 409 && !signal.aborted)
      await load(signal, false);
  } finally {
    if (!signal.aborted) busy.value = false;
  }
}

function review(decision: 'APPROVE' | 'RETURN') {
  if (!detail.value) return;
  const body = { decision, note: note.value.trim(), revision: detail.value.revision };
  if (decision === 'RETURN' && !body.note) {
    error.value = '退回资料时请填写原因。';
    return;
  }
  void mutate(
    body,
    (key, signal) => reviewVersion(project.value, versionId.value, body, key, signal),
    decision === 'APPROVE' ? '已批准，等待解析。' : '已退回。',
  );
}

function retry() {
  void mutate(
    ['retry', versionId.value],
    (key, signal) => retryIndex(project.value, versionId.value, key, signal),
    '已重新排队。',
  );
}

async function openReaders() {
  const signal = controller?.signal;
  if (!signal) return;
  try {
    readers.value = await getReaders(project.value, docId.value, signal);
    visibility.value = readers.value.visibility;
    selected.value = [...readers.value.userIds];
    await loadPeople(0);
  } catch (failure) {
    handle(failure, signal);
  }
}

async function loadPeople(page: number) {
  const signal = controller?.signal;
  if (!signal) return;
  try {
    const result = await listWorkspaceMembers(project.value, page, signal);
    people.value = result.items;
    readerPage.value = result.page;
    readerTotal.value = result.total;
  } catch (failure) {
    handle(failure, signal);
  }
}

function saveReaders() {
  if (!readers.value) return;
  const reviewer = ownerId.value;
  const userIds =
    visibility.value === 'RESTRICTED'
      ? [...new Set([...selected.value, ...(reviewer ? [reviewer] : [])])]
      : [];
  const body = { visibility: visibility.value, userIds, revision: readers.value.revision };
  void mutate(
    body,
    async (key, signal) => {
      readers.value = await setReaders(project.value, docId.value, body, key, signal);
      selected.value = [...readers.value.userIds];
    },
    '读者范围已保存。',
  );
}

async function submitted(item: WorkspaceDocument) {
  forming.value = false;
  await router.push(href(item.versionId));
}
</script>

<template>
  <section>
    <RouterLink v-if="returnToChat(project, route.query)" :to="returnToChat(project, route.query)"
      >回到原对话</RouterLink
    >
    <RouterLink :to="`/p/${project}/documents`">返回项目资料</RouterLink>
    <h1 data-page-heading tabindex="-1">{{ detail?.title ?? '项目资料' }}</h1>
    <p v-if="reading" role="status">正在读取资料…</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <template v-if="detail">
      <div class="detail-grid">
        <section class="detail-panel">
          <h2>正文</h2>
          <p v-if="!content" class="muted">解析就绪后显示正文。可以先下载有权访问的原件。</p>
          <div v-for="block in content?.blocks" :key="block.locator" class="source-block">
            <strong>{{ block.locator }}</strong>
            <p>{{ block.text }}</p>
          </div>
          <button v-if="content?.nextCursor" class="button button-secondary" @click="moreContent">
            加载后续正文
          </button>
        </section>
        <aside>
          <section class="detail-panel document-meta">
            <h2>{{ detail.versionLabel }}</h2>
            <p>审核 {{ reviews[detail.reviewStatus] }} · 解析 {{ indexes[detail.indexStatus] }}</p>
            <p>
              提交人 {{ detail.submittedBy.displayName }} ·
              {{ detail.visibility === 'PROJECT' ? '项目成员' : '限定读者' }}
            </p>
            <p v-if="detail.reason">{{ detail.reason }}</p>
            <p v-if="detail.errorCode" role="status">
              解析失败 ·
              {{ indexErrors[detail.errorCode] ?? detail.errorCode
              }}<span v-if="detail.errorCode === 'NO_TEXT'">，请提供可提取文字的版本。</span>
            </p>
            <div class="button-row">
              <button class="button button-secondary" @click="original">下载原件</button>
              <button class="button button-secondary" @click="forming = true">提交新版本</button>
              <button v-if="owner" class="button button-secondary" @click="openReaders">
                管理读者
              </button>
            </div>
            <div v-if="owner && detail.reviewStatus === 'PENDING'" class="review-form">
              <label>审核意见 <textarea v-model="note" rows="2" :disabled="busy" /></label>
              <div class="button-row">
                <button class="button button-primary" :disabled="busy" @click="review('APPROVE')">
                  批准并开始解析
                </button>
                <button class="button button-secondary" :disabled="busy" @click="review('RETURN')">
                  退回资料
                </button>
              </div>
            </div>
            <button
              v-if="owner && detail.indexStatus === 'FAILED'"
              class="button button-secondary"
              :disabled="busy"
              @click="retry"
            >
              重试解析
            </button>
            <h3>历史版本</h3>
            <ul>
              <li v-for="item in versions" :key="item.versionId">
                <RouterLink :to="href(item.versionId)"
                  >{{ item.versionLabel }} · {{ indexes[item.indexStatus] }}</RouterLink
                >
              </li>
            </ul>
            <div class="button-row">
              <button
                class="button button-secondary"
                :disabled="versionPage === 0"
                @click="loadVersions(versionPage - 1)"
              >
                上一页
              </button>
              <button
                class="button button-secondary"
                :disabled="(versionPage + 1) * 20 >= versionTotal"
                @click="loadVersions(versionPage + 1)"
              >
                下一页
              </button>
            </div>
          </section>
          <form v-if="readers" class="detail-panel readers-form" @submit.prevent="saveReaders">
            <h2>读者范围</h2>
            <label
              >可见性
              <select v-model="visibility" :disabled="busy">
                <option value="PROJECT">项目成员</option>
                <option value="RESTRICTED">限定读者</option>
              </select></label
            >
            <fieldset v-if="visibility === 'RESTRICTED'" :disabled="busy">
              <legend>可查看的成员</legend>
              <label v-for="person in people" :key="person.userId" class="reader">
                <input v-if="person.role === 'OWNER'" type="checkbox" checked disabled />
                <input v-else v-model="selected" type="checkbox" :value="person.userId" />
                {{ person.displayName }}{{ person.role === 'OWNER' ? '（审核负责人）' : '' }}
              </label>
              <div class="button-row">
                <button
                  type="button"
                  class="button button-secondary"
                  :disabled="readerPage === 0"
                  @click="loadPeople(readerPage - 1)"
                >
                  上一页
                </button>
                <button
                  type="button"
                  class="button button-secondary"
                  :disabled="(readerPage + 1) * 20 >= readerTotal"
                  @click="loadPeople(readerPage + 1)"
                >
                  下一页
                </button>
              </div>
            </fieldset>
            <div class="button-row">
              <button class="button button-primary" :disabled="busy">保存读者范围</button>
              <button
                type="button"
                class="button button-secondary"
                :disabled="busy"
                @click="readers = null"
              >
                关闭
              </button>
            </div>
          </form>
        </aside>
      </div>
      <WorkspaceDocumentForm
        v-if="forming"
        :project-id="project"
        :document="detail"
        @cancel="forming = false"
        @saved="submitted"
      />
    </template>
  </section>
</template>

<style scoped>
.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(18rem, 1fr);
  gap: 1rem;
}
.button-row {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}
.document-meta,
.review-form,
.review-form label,
.readers-form {
  display: grid;
  gap: 0.65rem;
}
.source-block p {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.source-block {
  border-bottom: 1px solid var(--border, #ddd);
  padding-block: 0.5rem;
}
.readers-form {
  margin-top: 1rem;
}
.reader {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-height: 44px;
}
button,
select,
.document-meta li a {
  min-height: 44px;
}
.document-meta li a {
  display: inline-flex;
  align-items: center;
}
@media (max-width: 800px) {
  .detail-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
