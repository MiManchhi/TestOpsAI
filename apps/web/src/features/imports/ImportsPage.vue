<script setup lang="ts">
import { computed, onScopeDispose, reactive, ref, watch } from 'vue';
import { projects } from '../projects/context';
import { idempotencyKey, useProjectAction, useResource } from '../../app/resource';
import RequestState from '../../app/RequestState.vue';
import PageControls from '../../app/PageControls.vue';
import { getImport, importCatalog, importRun, listImports, type CatalogFile } from './api';
import type { TestContext } from '../../app/contracts';
const project = projects.current;
const canWrite = computed(() => project.value?.role !== 'VIEWER');
const page = ref(0);
const kind = ref<'catalog' | 'run'>('catalog');
const files = ref<CatalogFile[]>([]);
const output = ref<File>();
const inputKey = ref(0);
const fileGeneration = ref(0);
const confirmed = ref(false);
const name = ref('');
const revision = ref('');
const context = reactive<TestContext>({
  deviceType: 'UNSPECIFIED',
  productModel: '',
  firmwareVersion: '',
  hardwareVersion: '',
  protocolName: '',
  protocolVersion: '',
});
const action = useProjectAction();
const getKey = idempotencyKey();
const selectedJob = ref('');
const history = useResource(
  () => String(page.value),
  (signal) => listImports(projects.state.selectedId, page.value, signal),
);
const job = useResource(
  () => selectedJob.value,
  (signal) =>
    selectedJob.value
      ? getImport(projects.state.selectedId, selectedJob.value, signal)
      : Promise.resolve(null),
);
const statusLabels = {
  QUEUED: '排队中',
  PROCESSING: '处理中',
  SUCCEEDED: '已完成',
  FAILED: '失败',
};
let timer: ReturnType<typeof setTimeout> | undefined;
let polls = 0;
const paused = ref(false);
watch([job.data, job.loading], () => {
  clearTimeout(timer);
  if (
    !job.loading.value &&
    job.data.value &&
    ['QUEUED', 'PROCESSING'].includes(job.data.value.status)
  ) {
    if (polls++ < 150) timer = setTimeout(() => void job.refresh(), 2000);
    else paused.value = true;
  }
});
watch(selectedJob, () => {
  polls = 0;
  paused.value = false;
});
onScopeDispose(() => clearTimeout(timer));
watch(
  () => projects.state.selectedId,
  () => {
    clearTimeout(timer);
    page.value = 0;
    files.value = [];
    output.value = undefined;
    selectedJob.value = '';
    name.value = revision.value = '';
    confirmed.value = false;
    Object.assign(context, {
      deviceType: 'UNSPECIFIED',
      productModel: '',
      firmwareVersion: '',
      hardwareVersion: '',
      protocolName: '',
      protocolVersion: '',
    });
    fileGeneration.value++;
    inputKey.value++;
  },
  { flush: 'sync' },
);
function chooseFiles(event: Event) {
  const target = event.target;
  if (!(target instanceof HTMLInputElement)) return;
  fileGeneration.value++;
  confirmed.value = false;
  if (kind.value === 'catalog')
    files.value = Array.from(target.files ?? []).map((file) => ({
      file,
      logicalPath: file.webkitRelativePath || file.name,
    }));
  else output.value = target.files?.[0];
}
function submit() {
  const id = projects.state.selectedId;
  const key = getKey(
    JSON.stringify([
      id,
      kind.value,
      fileGeneration.value,
      files.value.map((item) => item.logicalPath),
      name.value,
      revision.value,
      context,
    ]),
  );
  void action.run(
    async (signal) => {
      if (!canWrite.value) throw new Error('当前角色仅可查看导入记录。');
      if (kind.value === 'catalog') {
        if (!confirmed.value) throw new Error('请确认本次文件是完整目录快照。');
        return importCatalog(id, files.value, key, signal);
      }
      if (!output.value) throw new Error('请选择 output.xml。');
      return importRun(id, output.value, name.value, revision.value, { ...context }, key, signal);
    },
    async (accepted) => {
      selectedJob.value = accepted.jobId;
      action.notice.value = '导入任务已受理，处理结果以下方任务状态为准。';
      files.value = [];
      output.value = undefined;
      confirmed.value = false;
      fileGeneration.value++;
      inputKey.value++;
      page.value = 0;
      await history.reload();
    },
  );
}
</script>
<template>
  <div class="flow-page">
    <header class="page-heading-row">
      <div>
        <p class="section-kicker">DATA / 数据交换</p>
        <h1 data-page-heading tabindex="-1">数据导入</h1>
        <p class="muted">将 Robot 定义与运行产物带入当前项目，保留来源与版本。</p>
      </div>
    </header>
    <section v-if="canWrite" class="panel flow-panel">
      <div class="flow-tabs" aria-label="导入类型">
        <button
          class="button"
          :class="kind === 'catalog' ? 'button-primary' : 'button-secondary'"
          :aria-pressed="kind === 'catalog'"
          :disabled="action.busy.value"
          @click="kind = 'catalog'"
        >
          Robot 目录</button
        ><button
          class="button"
          :class="kind === 'run' ? 'button-primary' : 'button-secondary'"
          :aria-pressed="kind === 'run'"
          :disabled="action.busy.value"
          @click="kind = 'run'"
        >
          运行结果 output.xml
        </button>
      </div>
      <form class="flow-form" @submit.prevent="submit">
        <fieldset :disabled="action.busy.value">
          <template v-if="kind === 'catalog'">
            <h2>导入完整目录快照</h2>
            <p class="muted">
              最多 200 个 .robot/.resource 文件，单个 2 MiB，总计 20 MiB。仅静态解析。
            </p>
            <label class="file-drop"
              >选择源文件<input
                :key="inputKey"
                type="file"
                accept=".robot,.resource"
                multiple
                required
                @change="chooseFiles"
            /></label>
            <div v-if="files.length" class="file-list">
              <div v-for="(item, index) in files" :key="index">
                <span>{{ item.file.name }} · {{ (item.file.size / 1024).toFixed(1) }} KiB</span
                ><label :for="'logical-path-' + index">逻辑路径</label
                ><input
                  :id="'logical-path-' + index"
                  v-model="item.logicalPath"
                  maxlength="512"
                  required
                />
              </div>
            </div>
            <label class="flow-check"
              ><input
                v-model="confirmed"
                type="checkbox"
                required
              />我确认这是完整快照；已有快照中未包含的 Robot 用例将归档。</label
            >
          </template>
          <template v-else>
            <h2>导入运行结果</h2>
            <p class="muted">只接受 output.xml，最大 50 MiB。结果导入不会运行 Robot 或连接设备。</p>
            <label class="file-drop"
              >选择 output.xml<input
                :key="inputKey"
                type="file"
                accept=".xml"
                required
                @change="chooseFiles"
            /></label>
            <div class="form-grid">
              <label>运行名称<input v-model="name" maxlength="256" required /></label
              ><label
                >目录版本摘要<input
                  v-model="revision"
                  pattern="[a-f0-9]{64}"
                  minlength="64"
                  maxlength="64"
                  required
                  placeholder="64 位 SHA-256"
              /></label>
              <label
                >表型<select v-model="context.deviceType" aria-label="表型">
                  <option value="UNSPECIFIED">未指定</option>
                  <option value="SINGLE_PHASE">单相表</option>
                  <option value="THREE_PHASE">三相表</option>
                </select></label
              >
              <label>产品型号<input v-model="context.productModel" maxlength="128" /></label
              ><label>固件版本<input v-model="context.firmwareVersion" maxlength="64" /></label
              ><label>硬件版本<input v-model="context.hardwareVersion" maxlength="64" /></label
              ><label>规约名称<input v-model="context.protocolName" maxlength="128" /></label
              ><label>规约版本<input v-model="context.protocolVersion" maxlength="64" /></label>
            </div>
          </template>
          <button class="button button-primary" type="submit" :disabled="action.busy.value">
            {{ action.busy.value ? '正在提交…' : '创建导入任务' }}
          </button>
        </fieldset>
      </form>
      <p v-if="action.error.value" class="flow-error" role="alert">
        {{ action.error.value
        }}<small v-if="action.trace.value">请求编号：{{ action.trace.value }}</small>
      </p>
      <p v-if="action.notice.value" class="notice" role="status">{{ action.notice.value }}</p>
    </section>
    <p v-else class="notice">当前角色可查看导入历史；创建导入任务需要编辑者或所有者权限。</p>
    <section v-if="selectedJob" class="panel flow-panel">
      <div class="panel-heading">
        <h2>导入任务</h2>
        <button
          class="text-button"
          :disabled="job.loading.value"
          @click="
            polls = 0;
            paused = false;
            job.reload();
          "
        >
          刷新状态
        </button>
      </div>
      <RequestState
        :loading="job.loading.value && !job.data.value"
        :error="job.error.value"
        :trace="job.trace.value"
        @retry="job.reload"
      />
      <template v-if="job.data.value"
        ><p>
          <span class="status-tag" :data-status="job.data.value.status">{{
            statusLabels[job.data.value.status]
          }}</span>
          <code>{{ job.data.value.id }}</code>
        </p>
        <dl class="flow-metrics">
          <div>
            <dt>新增</dt>
            <dd>{{ job.data.value.counts.added }}</dd>
          </div>
          <div>
            <dt>更新</dt>
            <dd>{{ job.data.value.counts.updated }}</dd>
          </div>
          <div>
            <dt>归档</dt>
            <dd>{{ job.data.value.counts.archived }}</dd>
          </div>
          <div>
            <dt>未映射</dt>
            <dd>{{ job.data.value.counts.unmapped }}</dd>
          </div>
        </dl>
        <p v-if="job.data.value.revision" class="flow-hash">
          目录版本：{{ job.data.value.revision }}
        </p>
        <RouterLink
          v-if="job.data.value.runId"
          class="text-button"
          :to="{ path: '/runs/' + job.data.value.runId, query: { projectId: project?.id } }"
          >查看运行详情 →</RouterLink
        >
        <ul v-if="job.data.value.issues.length" class="issue-list">
          <li v-for="(issue, index) in job.data.value.issues" :key="index">
            <strong>{{ issue.code }}</strong> · {{ issue.sourcePath
            }}{{ issue.line ? ':' + issue.line : '' }}
            <p>{{ issue.message }}</p>
          </li>
        </ul>
      </template>
      <p v-if="paused" role="status">已暂停自动刷新，可手动刷新查看最新状态。</p>
    </section>
    <section class="panel flow-panel">
      <div class="panel-heading">
        <h2>导入历史</h2>
        <button class="text-button" :disabled="history.loading.value" @click="history.reload">
          刷新
        </button>
      </div>
      <RequestState
        :loading="history.loading.value"
        :error="history.error.value"
        :trace="history.trace.value"
        @retry="history.reload"
      />
      <template v-if="history.data.value"
        ><p v-if="!history.data.value.items.length" class="flow-empty">
          还没有导入记录。完成首次导入后，可在这里追踪处理结果。
        </p>
        <div v-else class="flow-table-scroll">
          <table class="flow-table">
            <thead>
              <tr>
                <th>类型 / 编号</th>
                <th>创建时间</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in history.data.value.items" :key="item.id">
                <td>
                  <strong>{{
                    item.kind === 'ROBOT_CATALOG' ? 'Robot 目录' : '运行结果'
                  }}</strong><small>{{ item.id }}</small>
                </td>
                <td>{{ new Date(item.createdAt).toLocaleString('zh-CN') }}</td>
                <td>
                  <span class="status-tag" :data-status="item.status">{{
                    statusLabels[item.status]
                  }}</span>
                </td>
                <td>
                  <button
                    class="text-button"
                    @click="
                      selectedJob = item.id;
                      job.reload();
                    "
                  >
                    查看任务
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PageControls
          :page="page"
          :size="20"
          :total="history.data.value.total"
          :busy="history.loading.value"
          @change="page = $event"
        />
      </template>
    </section>
  </div>
</template>
