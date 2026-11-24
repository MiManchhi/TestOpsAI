<script setup lang="ts">
import ProjectSettingsNav from '../projects/ProjectSettingsNav.vue';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ApiError, errorMessage } from '../../app/http';
import type { Basis, WorkspaceDocument } from '../../app/workspace-contracts';
import { listBases, listDocuments, listDocumentVersions, publishBasis } from './workspace-api';

const route = useRoute();
const project = computed(() => String(route.params.projectId));
const docs = ref<WorkspaceDocument[]>([]);
const versions = ref<WorkspaceDocument[]>([]);
const bundles = ref<Basis[]>([]);
const chosen = ref<WorkspaceDocument[]>([]);
const label = ref('');
const selected = ref<WorkspaceDocument | null>(null);
const page = ref(0);
const total = ref(0);
const versionPage = ref(0);
const versionTotal = ref(0);
const basisPage = ref(0);
const basisTotal = ref(0);
const revision = ref(0);
const error = ref('');
const notice = ref('');
const busy = ref(false);
let session: AbortController | undefined;
let request: { key: string; fingerprint: string } | undefined;
let picker: AbortController | undefined;

watch(
  project,
  () => {
    session?.abort();
    picker?.abort();
    session = new AbortController();
    selected.value = null;
    docs.value = versions.value = chosen.value = [];
    bundles.value = [];
    request = undefined;
    error.value = notice.value = label.value = '';
    busy.value = false;
    void initialize(session.signal);
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  session?.abort();
  picker?.abort();
});

async function initialize(signal: AbortSignal) {
  try {
    const result = await listBases(project.value, 0, signal);
    if (signal.aborted) return;
    bundles.value = result.items;
    revision.value = result.items[0]?.revision ?? 0;
    basisTotal.value = result.total;
    basisPage.value = 0;
    await loadDocuments(0);
  } catch (failure) {
    if (!signal.aborted) error.value = errorMessage(failure);
  }
}

async function loadDocuments(p: number) {
  const signal = session?.signal;
  if (!signal) return;
  try {
    const result = await listDocuments(project.value, p, signal, '', 'APPROVED');
    docs.value = result.items;
    page.value = result.page;
    total.value = result.total;
  } catch (failure) {
    if (!signal.aborted) error.value = errorMessage(failure);
  }
}

async function loadHistory(p: number) {
  const signal = session?.signal;
  if (!signal) return;
  try {
    const result = await listBases(project.value, p, signal);
    bundles.value = result.items;
    basisPage.value = result.page;
    basisTotal.value = result.total;
  } catch (failure) {
    if (!signal.aborted) error.value = errorMessage(failure);
  }
}

async function pick(doc: WorkspaceDocument, p = 0) {
  picker?.abort();
  picker = new AbortController();
  const signal = picker.signal;
  selected.value = doc;
  versions.value = [];
  try {
    const result = await listDocumentVersions(project.value, doc.documentId, p, signal);
    if (signal.aborted) return;
    versions.value = result.items.filter(
      (item) => item.reviewStatus === 'APPROVED' && item.indexStatus === 'READY',
    );
    versionPage.value = result.page;
    versionTotal.value = result.total;
  } catch (failure) {
    if (!signal.aborted) error.value = errorMessage(failure);
  }
}

function choose(item: WorkspaceDocument) {
  chosen.value = [...chosen.value.filter((other) => other.documentId !== item.documentId), item];
}

async function submit() {
  const signal = session?.signal;
  if (!signal || busy.value || !chosen.value.length || !label.value.trim()) return;
  const body = {
    label: label.value.trim(),
    versionIds: chosen.value.map((item) => item.versionId),
    expectedRevision: revision.value,
  };
  const fingerprint = JSON.stringify(body);
  if (request?.fingerprint !== fingerprint) request = { fingerprint, key: crypto.randomUUID() };
  error.value = notice.value = '';
  busy.value = true;
  try {
    const result = await publishBasis(project.value, body, request.key, signal);
    request = undefined;
    chosen.value = [];
    label.value = '';
    revision.value = result.revision;
    notice.value = '整套验收依据已生效。';
    await loadHistory(0);
  } catch (failure) {
    if (!signal.aborted) {
      error.value = errorMessage(failure);
      if (failure instanceof ApiError && failure.status === 409) await initialize(signal);
    }
  } finally {
    if (!signal.aborted) busy.value = false;
  }
}

const format = (value: string) => new Date(value).toLocaleString('zh-CN');
</script>

<template>
  <section>
    <RouterLink :to="`/p/${project}/documents`">返回项目资料</RouterLink>
    <h1 data-page-heading tabindex="-1">项目验收依据</h1>
    <ProjectSettingsNav :project="String(route.params.projectId)" />
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <section class="detail-panel">
      <h2>选择资料版本</h2>
      <p v-if="!docs.length" class="muted">暂无已批准资料</p>
      <div v-else class="button-row">
        <button
          v-for="doc in docs"
          :key="doc.documentId"
          class="button button-secondary"
          :disabled="busy"
          @click="pick(doc)"
        >
          {{ doc.title }}
        </button>
      </div>
      <div class="button-row">
        <button
          class="button button-secondary"
          :disabled="page === 0"
          @click="loadDocuments(page - 1)"
        >
          上页资料
        </button>
        <button
          class="button button-secondary"
          :disabled="(page + 1) * 20 >= total"
          @click="loadDocuments(page + 1)"
        >
          下页资料
        </button>
      </div>
      <section v-if="selected">
        <h3>{{ selected.title }} · 就绪版本</h3>
        <p v-if="!versions.length" class="muted">这一页没有已就绪的版本。</p>
        <div class="button-row">
          <button
            v-for="item in versions"
            :key="item.versionId"
            class="button button-secondary"
            :disabled="busy"
            @click="choose(item)"
          >
            选用 {{ item.versionLabel }}
          </button>
        </div>
        <div class="button-row">
          <button
            class="button button-secondary"
            :disabled="versionPage === 0"
            @click="pick(selected, versionPage - 1)"
          >
            上页版本
          </button>
          <button
            class="button button-secondary"
            :disabled="(versionPage + 1) * 20 >= versionTotal"
            @click="pick(selected, versionPage + 1)"
          >
            下页版本
          </button>
        </div>
      </section>
    </section>
    <form class="detail-panel publication" @submit.prevent="submit">
      <h2>发布整套依据</h2>
      <label
        >验收包名称
        <input
          v-model="label"
          maxlength="160"
          required
          :disabled="busy"
          placeholder="例如 2026 年验收依据 V2"
      /></label>
      <ul v-if="chosen.length">
        <li v-for="item in chosen" :key="item.documentId">
          {{ item.title }} · {{ item.versionLabel }}
          <button
            type="button"
            class="button button-secondary"
            :disabled="busy"
            @click="chosen = chosen.filter((value) => value.documentId !== item.documentId)"
          >
            移除
          </button>
        </li>
      </ul>
      <p v-else class="muted">请选择至少一份资料的就绪版本。</p>
      <button class="button button-primary" :disabled="busy || !chosen.length">
        {{ busy ? '正在发布…' : '发布并生效' }}
      </button>
    </form>
    <section class="detail-panel">
      <h2>生效历史</h2>
      <p v-if="!bundles.length" class="muted">尚未发布验收依据。</p>
      <ul>
        <li v-for="item in bundles" :key="item.basisId">
          <strong>{{ item.label }}</strong>
          <p>
            {{ format(item.effectiveFrom) }} 至
            {{ item.effectiveTo ? format(item.effectiveTo) : '当前' }} ·
            {{ item.versionIds.length }} 份资料
          </p>
        </li>
      </ul>
      <div class="button-row">
        <button
          class="button button-secondary"
          :disabled="basisPage === 0"
          @click="loadHistory(basisPage - 1)"
        >
          上页历史
        </button>
        <button
          class="button button-secondary"
          :disabled="(basisPage + 1) * 20 >= basisTotal"
          @click="loadHistory(basisPage + 1)"
        >
          下页历史
        </button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.detail-panel {
  margin-block: 0.8rem;
  display: grid;
  gap: 0.6rem;
}
.publication label {
  display: grid;
  gap: 0.45rem;
}
.button-row {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  flex-wrap: wrap;
}
button,
input {
  min-height: 44px;
}
</style>
