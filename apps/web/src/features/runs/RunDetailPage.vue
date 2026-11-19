<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { projects } from '../projects/context';
import { useResource } from '../../app/resource';
import RequestState from '../../app/RequestState.vue';
import PageControls from '../../app/PageControls.vue';
import { getRun, listResults, listArtifacts, getArtifactLines, type ResultStatus } from './api';
const route = useRoute();
const id = computed(() => String(route.params.runId ?? ''));
const page = ref(0);
const artifactPage = ref(0);
const status = ref<ResultStatus>('');
const artifactId = ref('');
const fromLine = ref(1);
watch(
  [id, () => projects.state.selectedId],
  () => {
    page.value = artifactPage.value = 0;
    status.value = '';
    artifactId.value = '';
    fromLine.value = 1;
  },
  { flush: 'sync' },
);
watch(status, () => {
  page.value = 0;
});
const run = useResource(
  () => id.value,
  (signal) => getRun(projects.state.selectedId, id.value, signal),
);
const results = useResource(
  () => `${id.value}:${page.value}:${status.value}`,
  (signal) => listResults(projects.state.selectedId, id.value, page.value, status.value, signal),
);
const artifacts = useResource(
  () => `${id.value}:${artifactPage.value}`,
  (signal) => listArtifacts(projects.state.selectedId, id.value, artifactPage.value, signal),
);
const lines = useResource(
  () => `${artifactId.value}:${fromLine.value}`,
  (signal) =>
    artifactId.value
      ? getArtifactLines(projects.state.selectedId, artifactId.value, fromLine.value, signal)
      : Promise.resolve(null),
);
function openLines(artifact: string, line = 1) {
  artifactId.value = artifact;
  fromLine.value = line;
}
const query = computed(() => ({ projectId: projects.state.selectedId }));
</script>
<template>
  <div class="flow-page">
    <RouterLink class="text-button" :to="{ path: '/runs', query }">← 返回运行记录</RouterLink>
    <header class="page-heading-row">
      <div>
        <p class="section-kicker">RUN / 当次运行快照</p>
        <h1 data-page-heading tabindex="-1">{{ run.data.value?.name ?? '运行详情' }}</h1>
        <p class="muted">原始结果只读，可定位失败信息与脱敏文本日志。</p>
      </div>
      <RouterLink
        v-if="run.data.value"
        class="button button-primary"
        :to="{ path: '/assistant', query: { ...query, runId: run.data.value.id } }"
        >让 AI 分析本次运行 →</RouterLink
      >
      <button
        class="button button-secondary"
        @click="
          run.reload();
          results.reload();
          artifacts.reload();
        "
      >
        刷新详情
      </button>
    </header>
    <RequestState
      :loading="run.loading.value"
      :error="run.error.value"
      :trace="run.trace.value"
      @retry="run.reload"
    />
    <section v-if="run.data.value" class="panel flow-panel">
      <div class="panel-heading">
        <h2>结果摘要</h2>
        <span class="status-tag" :data-status="run.data.value.status">{{
          run.data.value.status === 'COMPLETED' ? '完整' : '不完整 · 包含未映射结果'
        }}</span>
      </div>
      <p v-if="run.data.value.origin === 'MOCK'" class="notice">这是模拟数据。</p>
      <dl class="flow-metrics">
        <div>
          <dt>总计</dt>
          <dd>{{ run.data.value.counts.total }}</dd>
        </div>
        <div>
          <dt>PASS</dt>
          <dd class="result-pass">{{ run.data.value.counts.pass }}</dd>
        </div>
        <div>
          <dt>FAIL</dt>
          <dd class="result-fail">{{ run.data.value.counts.fail }}</dd>
        </div>
        <div>
          <dt>SKIP</dt>
          <dd>{{ run.data.value.counts.skip }}</dd>
        </div>
      </dl>
      <dl class="metadata-list">
        <div>
          <dt>开始时间</dt>
          <dd>
            {{
              run.data.value.startedAt
                ? new Date(run.data.value.startedAt).toLocaleString('zh-CN')
                : '未记录'
            }}
          </dd>
        </div>
        <div>
          <dt>耗时</dt>
          <dd>
            {{ run.data.value.elapsedMs === null ? '未记录' : run.data.value.elapsedMs + ' ms' }}
          </dd>
        </div>
        <div>
          <dt>产品 / 固件</dt>
          <dd>
            {{ run.data.value.testContext.productModel || '未指定' }} /
            {{ run.data.value.testContext.firmwareVersion || '未指定' }}
          </dd>
        </div>
      </dl>
      <p class="flow-hash">目录版本：{{ run.data.value.catalogRevision }}</p>
    </section>
    <section class="panel flow-panel">
      <div class="flow-toolbar">
        <h2>用例结果</h2>
        <label
          >结果筛选<select v-model="status" aria-label="结果筛选">
            <option value="">全部结果</option>
            <option value="PASS">PASS</option>
            <option value="FAIL">FAIL</option>
            <option value="SKIP">SKIP</option>
          </select></label
        >
      </div>
      <RequestState
        :loading="results.loading.value"
        :error="results.error.value"
        :trace="results.trace.value"
        @retry="results.reload"
      />
      <template v-if="results.data.value"
        ><p v-if="!results.data.value.items.length" class="flow-empty">没有符合条件的结果。</p>
        <div v-else class="result-list">
          <article v-for="result in results.data.value.items" :key="result.id" class="result-entry">
            <div class="panel-heading">
              <div>
                <span class="status-tag" :data-status="result.status">{{
                  result.status
                }}</span><strong>{{ result.caseName }}</strong>
              </div>
              <span class="muted">{{ result.elapsedMs }} ms</span>
            </div>
            <small>{{ result.suitePath }}</small>
            <p v-if="result.failedKeyword">失败关键字：{{ result.failedKeyword }}</p>
            <pre v-if="result.message" class="flow-message">{{ result.message }}</pre>
            <div class="flow-actions">
              <RouterLink
                v-if="result.caseId"
                class="text-button"
                :to="{ path: '/test-cases/' + result.caseId, query }"
                >查看用例定义 →</RouterLink
              ><span v-else class="muted">未映射至目录用例</span
              ><button
                class="text-button"
                @click="openLines(result.artifactId, result.sourceLine ?? 1)"
              >
                定位原始证据{{ result.sourceLine ? ' · 第 ' + result.sourceLine + ' 行' : '' }}
              </button>
            </div>
          </article>
        </div>
        <PageControls
          :page="page"
          :size="20"
          :total="results.data.value.total"
          :busy="results.loading.value"
          @change="page = $event"
      /></template>
    </section>
    <section class="panel flow-panel">
      <h2>运行产物</h2>
      <RequestState
        :loading="artifacts.loading.value"
        :error="artifacts.error.value"
        :trace="artifacts.trace.value"
        @retry="artifacts.reload"
      />
      <template v-if="artifacts.data.value"
        ><p v-if="!artifacts.data.value.items.length" class="flow-empty">暂无运行产物。</p>
        <ul v-else class="artifact-list">
          <li v-for="artifact in artifacts.data.value.items" :key="artifact.id">
            <div>
              <strong>{{ artifact.name }}</strong><small
                >{{ artifact.kind }} · {{ (artifact.sizeBytes / 1024).toFixed(1) }} KiB</small
              >
            </div>
            <button
              v-if="artifact.kind === 'LOG_TEXT' || artifact.kind === 'ROBOT_OUTPUT_XML'"
              class="text-button"
              @click="openLines(artifact.id)"
            >
              查看文本</button
            ><span v-else class="muted">HTML 产物不在页面内执行</span>
          </li>
        </ul>
        <PageControls
          :page="artifactPage"
          :size="20"
          :total="artifacts.data.value.total"
          @change="artifactPage = $event"
      /></template>
    </section>
    <section v-if="artifactId" class="panel flow-panel" aria-label="日志证据">
      <div class="panel-heading">
        <h2>日志证据 · #{{ artifactId }}</h2>
        <button class="text-button" @click="artifactId = ''">关闭日志</button>
      </div>
      <RequestState
        :loading="lines.loading.value"
        :error="lines.error.value"
        :trace="lines.trace.value"
        @retry="lines.reload"
      />
      <template v-if="lines.data.value"
        ><div class="log-lines">
          <div v-for="(line, index) in lines.data.value.lines" :key="index">
            <span>{{ lines.data.value.fromLine + index }}</span><code>{{ line }}</code>
          </div>
        </div>
        <div class="flow-actions">
          <button
            class="button button-secondary"
            :disabled="fromLine === 1"
            @click="fromLine = Math.max(1, fromLine - 100)"
          >
            前 100 行</button
          ><button
            class="button button-secondary"
            :disabled="!lines.data.value.hasMore"
            @click="fromLine += lines.data.value.lines.length"
          >
            后续行
          </button>
        </div></template
      >
    </section>
  </div>
</template>
