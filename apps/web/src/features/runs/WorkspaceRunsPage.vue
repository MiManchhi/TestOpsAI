<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { errorMessage } from '../../app/http';
import type {
  RunArtifact,
  RunLogLine,
  RunResult,
  WorkspaceRun,
} from '../../app/workspace-contracts';
import { handoffFiles, handoffId, returnToChat } from '../workspace/chat-handoff';
import { uploadAttachment } from '../knowledge/workspace-api';
import * as api from './workspace-runs';

const route = useRoute();
const router = useRouter();
const project = String(route.params.projectId);
const runId = typeof route.params.runId === 'string' ? route.params.runId : '';
const root = '/p/' + project;
const base = root + '/runs';
const creating = route.path.endsWith('/runs/new');
const inheritedOutput = ref(creating ? handoffId(route.query.output) : '');
const inheritedLogs = ref(creating ? handoffFiles(route.query.logs) : []);
const returnPath = returnToChat(project, route.query);
const q = ref('');
const list = ref<WorkspaceRun[]>([]);
const run = ref<WorkspaceRun>();
const artifacts = ref<RunArtifact[]>([]);
const results = ref<RunResult[]>([]);
const log = ref<RunLogLine[]>([]);
const logId = ref('');
const logStart = ref(1);
const listPage = ref(0);
const resultPage = ref(0);
const logPage = ref(0);
const moreList = ref(false);
const moreResults = ref(false);
const moreLog = ref(false);
const error = ref('');
const busy = ref(false);
const status = {
  QUEUED: '等待解析',
  PROCESSING: '正在解析',
  READY: '解析就绪',
  FAILED: '解析失败',
};
const name = ref('');
const executionTime = ref('');
const output = ref<File>();
const logs = ref<File[]>([]);
let key = crypto.randomUUID();
let uploaded: string[] = [];
const controller = new AbortController();
const signal = controller.signal;
let refresh: ReturnType<typeof setTimeout> | undefined;
const currentLog = computed(() => artifacts.value.find((file) => file.artifactId === logId.value));

async function loadList(page = 0) {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    const response = await api.list(project, page, q.value, signal);
    if (signal.aborted) return;
    list.value = response.items;
    listPage.value = page;
    moreList.value = (page + 1) * response.size < response.total;
  } catch (cause) {
    if (!signal.aborted) error.value = errorMessage(cause);
  } finally {
    if (!signal.aborted) busy.value = false;
  }
}
async function loadResults(page = 0) {
  try {
    const response = await api.results(project, runId, page, signal);
    if (signal.aborted) return;
    results.value = response.items;
    resultPage.value = page;
    moreResults.value = (page + 1) * response.size < response.total;
  } catch (cause) {
    if (!signal.aborted) error.value = errorMessage(cause);
  }
}
async function loadDetail() {
  try {
    const [record, files] = await Promise.all([
      api.detail(project, runId, signal),
      api.artifacts(project, runId, signal),
    ]);
    if (signal.aborted) return;
    run.value = record;
    artifacts.value = files.items;
    if (record.parseStatus === 'READY') {
      await loadResults();
      const aid = typeof route.query.artifactId === 'string' ? route.query.artifactId : '';
      const line = Number(route.query.fromLine ?? 1);
      if (
        files.items.some((item) => item.artifactId === aid && item.kind === 'LOG_TEXT') &&
        Number.isSafeInteger(line) &&
        line > 0
      )
        void loadLog(aid, 0, line);
    } else if (record.parseStatus !== 'FAILED') {
      refresh = setTimeout(() => void loadDetail(), 1250);
    }
  } catch (cause) {
    if (!signal.aborted) error.value = errorMessage(cause);
  }
}
async function loadLog(id: string, index = 0, fromLine = 1) {
  logId.value = id;
  logStart.value = fromLine;
  try {
    const response = await api.lines(project, runId, id, index, signal, fromLine);
    if (signal.aborted || logId.value !== id) return;
    log.value = response.items;
    logPage.value = index;
    moreLog.value = (index + 1) * response.size < response.total;
  } catch (cause) {
    if (!signal.aborted) error.value = errorMessage(cause);
  }
}
async function download(file: RunArtifact) {
  try {
    const content = await api.download(project, runId, file.artifactId, signal);
    if (signal.aborted) return;
    const url = URL.createObjectURL(content);
    const link = document.createElement('a');
    link.href = url;
    link.download = file.name;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (cause) {
    if (!signal.aborted) error.value = errorMessage(cause);
  }
}
function takeFiles(event: Event, primary: boolean) {
  const element = event.target as HTMLInputElement;
  if (primary) {
    output.value = element.files?.[0];
    if (output.value) inheritedOutput.value = '';
  } else {
    logs.value = Array.from(element.files ?? []);
    if (logs.value.length) inheritedLogs.value = [];
  }
  uploaded = [];
  key = crypto.randomUUID();
}
function edited() {
  key = crypto.randomUUID();
}
async function submit() {
  if (
    busy.value ||
    (!output.value && !inheritedOutput.value) ||
    !executionTime.value ||
    !name.value.trim()
  )
    return;
  if (output.value && output.value.name.toLowerCase() !== 'output.xml') {
    error.value = '请选择 Robot Framework 生成的 output.xml。';
    return;
  }
  error.value = '';
  busy.value = true;
  try {
    const all = output.value ? [output.value, ...logs.value] : logs.value;
    for (const file of all.slice(uploaded.length)) {
      const attached = await uploadAttachment(project, file, crypto.randomUUID(), signal);
      if (signal.aborted) return;
      uploaded.push(attached.attachmentId);
    }
    const created = await api.register(
      project,
      {
        name: name.value.trim(),
        executedAt: new Date(executionTime.value).toISOString(),
        outputAttachmentId: inheritedOutput.value || uploaded[0] || '',
        logAttachmentIds: [
          ...inheritedLogs.value,
          ...uploaded.slice(inheritedOutput.value ? 0 : 1),
        ],
      },
      key,
      signal,
    );
    if (!signal.aborted)
      await router.push({
        path: base + '/' + created.runId,
        query: returnPath
          ? { returnConversationId: handoffId(route.query.returnConversationId) }
          : {},
      });
  } catch (cause) {
    if (!signal.aborted) error.value = errorMessage(cause);
  } finally {
    if (!signal.aborted) busy.value = false;
  }
}
onMounted(() => {
  if (runId) void loadDetail();
  else if (!creating) void loadList();
});
onBeforeUnmount(() => {
  controller.abort();
  clearTimeout(refresh);
});
</script>

<template>
  <section class="runs">
    <RouterLink v-if="returnPath" :to="returnPath">回到原对话</RouterLink>
    <RouterLink v-if="runId || creating" :to="base">返回运行记录</RouterLink>
    <header class="run-head">
      <h1 data-page-heading tabindex="-1">
        {{ creating ? '登记复测' : runId ? (run?.name ?? '运行详情') : '运行记录' }}
      </h1>
      <RouterLink v-if="!creating && !runId" class="button button-primary" :to="base + '/new'"
        >登记复测</RouterLink
      >
      <RouterLink
        v-if="run?.parseStatus === 'READY'"
        class="button button-primary"
        :to="{ path: root + '/chat', query: { runId } }"
        >问 AI 核对本次运行</RouterLink
      >
    </header>
    <p v-if="error" class="notice notice-error" role="alert">{{ error }}</p>
    <form v-if="creating" class="detail-panel run-form" @submit.prevent="submit">
      <label
        >运行名称<input v-model="name" required maxlength="160" :disabled="busy" @input="edited"
      /></label>
      <label
        >执行时间<input
          v-model="executionTime"
          type="datetime-local"
          required
          :disabled="busy"
          @input="edited"
      /></label>
      <label
        >Robot 结果文件<input
          type="file"
          accept=".xml"
          :required="!inheritedOutput"
          :disabled="busy"
          @change="takeFiles($event, true)"
      /></label>
      <p v-if="inheritedOutput">已带入原对话的 output.xml。选择新文件可替换。</p>
      <p v-if="inheritedLogs.length">
        已带入 {{ inheritedLogs.length }} 份日志。
        <button
          type="button"
          class="text-button"
          :disabled="busy"
          @click="
            inheritedLogs = [];
            edited();
          "
        >
          移除
        </button>
      </p>
      <label
        >日志（可选）<input
          type="file"
          accept=".txt,.log"
          multiple
          :disabled="busy"
          @change="takeFiles($event, false)"
      /></label>
      <button class="button button-primary" :disabled="busy">
        {{ busy ? '正在登记…' : '登记复测' }}
      </button>
    </form>
    <template v-else-if="runId">
      <p v-if="!run && !error" role="status">正在读取运行记录…</p>
      <template v-if="run">
        <section class="detail-panel run-summary">
          <div>
            <b>{{ status[run.parseStatus] }}</b>
            <span v-if="run.errorCode">{{ run.errorCode }}</span>
          </div>
          <p>{{ new Date(run.executedAt).toLocaleString() }} · {{ run.providedBy.displayName }}</p>
          <p v-if="run.parseStatus === 'READY'">
            共 {{ run.total }} 项 · PASS {{ run.pass }} · FAIL {{ run.fail }} · SKIP {{ run.skip }}
          </p>
        </section>
        <div class="run-columns">
          <section class="detail-panel">
            <h2>用例结果</h2>
            <p v-if="!results.length">暂无可显示的结果</p>
            <div v-else class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>用例</th>
                    <th>套件</th>
                    <th>Robot 状态</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in results" :key="row.resultId">
                    <td>{{ row.caseName }}</td>
                    <td>{{ row.suite }}</td>
                    <td>{{ row.robotStatus }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="run-actions">
              <button
                v-if="resultPage"
                class="button button-secondary"
                @click="loadResults(resultPage - 1)"
              >
                上一页
              </button>
              <button
                v-if="moreResults"
                class="button button-secondary"
                @click="loadResults(resultPage + 1)"
              >
                下一页
              </button>
            </div>
          </section>
          <section class="detail-panel">
            <h2>原始文件</h2>
            <ul class="file-list">
              <li v-for="file in artifacts" :key="file.artifactId">
                <span>{{ file.name }}</span>
                <button class="text-button" @click="download(file)">下载</button>
                <button
                  v-if="file.kind === 'LOG_TEXT'"
                  class="text-button"
                  @click="loadLog(file.artifactId)"
                >
                  查看日志
                </button>
              </li>
            </ul>
            <template v-if="currentLog">
              <h3>{{ currentLog.name }}</h3>
              <pre><span v-for="line in log" :key="line.line">{{ line.line }}  {{ line.text }}{{ '\n' }}</span></pre>
              <div class="run-actions">
                <button
                  v-if="logPage"
                  class="button button-secondary"
                  @click="loadLog(logId, logPage - 1, logStart)"
                >
                  上一页日志
                </button>
                <button
                  v-if="moreLog"
                  class="button button-secondary"
                  @click="loadLog(logId, logPage + 1, logStart)"
                >
                  下一页日志
                </button>
              </div>
            </template>
          </section>
        </div>
      </template>
    </template>
    <template v-else>
      <form class="run-actions search" @submit.prevent="loadList(0)">
        <input v-model="q" type="search" aria-label="搜索运行" placeholder="搜索运行名称" />
        <button class="button button-secondary" :disabled="busy">搜索</button>
      </form>
      <section class="detail-panel">
        <p v-if="!list.length" role="status">{{ busy ? '正在读取运行记录…' : '暂无运行记录' }}</p>
        <div v-else class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>运行名称</th>
                <th>执行时间</th>
                <th>提供人</th>
                <th>解析状态</th>
                <th>Robot 执行结果</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in list" :key="row.runId">
                <td>
                  <RouterLink :to="base + '/' + row.runId">{{ row.name }}</RouterLink>
                </td>
                <td>{{ new Date(row.executedAt).toLocaleString() }}</td>
                <td>{{ row.providedBy.displayName }}</td>
                <td>
                  <span class="run-status" :data-ready="row.parseStatus === 'READY'">{{
                    status[row.parseStatus]
                  }}</span>
                </td>
                <td>
                  <template v-if="row.parseStatus === 'READY'"
                    >通过 {{ row.pass }} · 失败 {{ row.fail }} · 跳过 {{ row.skip }}</template
                  ><span v-else>—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="run-actions">
          <button v-if="listPage" class="button button-secondary" @click="loadList(listPage - 1)">
            上一页
          </button>
          <button v-if="moreList" class="button button-secondary" @click="loadList(listPage + 1)">
            下一页
          </button>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.runs {
  display: grid;
  gap: 18px;
}
.run-head,
.run-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.run-actions {
  justify-content: flex-start;
}
.run-form {
  display: grid;
  max-width: 600px;
  gap: 14px;
}
.run-form label {
  display: grid;
  gap: 6px;
}
.run-form button {
  justify-self: start;
}
.run-form input,
.search input {
  border: 1px solid #bfcfc4;
  border-radius: 6px;
  padding: 9px;
}
.run-columns {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: 18px;
}
.run-summary {
  display: grid;
  gap: 8px;
}
.table-scroll {
  overflow-x: auto;
}
table {
  border-collapse: collapse;
  width: 100%;
}
th,
td {
  text-align: left;
  border-bottom: 1px solid #d9e2dc;
  padding: 10px;
}
td a {
  color: var(--primary);
  font-weight: 650;
}
.run-status {
  font-size: 12px;
  padding: 3px 7px;
  border-radius: 5px;
  background: #eef2f9;
}
.run-status[data-ready='true'] {
  color: #276249;
  background: #e3f3eb;
}
.file-list {
  list-style: none;
  padding-left: 0;
}
.file-list li {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
pre {
  overflow: auto;
  background: var(--canvas);
  padding: 10px;
}
@media (max-width: 700px) {
  .run-columns {
    grid-template-columns: 1fr;
  }
}
</style>
