<script setup lang="ts">
import { computed } from 'vue';
import { projects } from '../projects/context';
import { getOverview } from '../../app/api-contract';
import { useResource } from '../../app/resource';
import RequestState from '../../app/RequestState.vue';
const project = projects.current;
const overview = useResource(
  () => projects.state.selectedId,
  (signal) => getOverview(projects.state.selectedId, signal),
);
const query = computed(() => ({ projectId: projects.state.selectedId }));
</script>
<template>
  <div class="flow-page">
    <header class="page-heading-row">
      <div>
        <p class="section-kicker">METER TESTING / 测试协作</p>
        <h1 data-page-heading tabindex="-1">工作台</h1>
        <p class="muted">{{ project?.name }} · 从测试事实到可追溯的证据。</p>
      </div>
      <button
        class="button button-secondary"
        :disabled="overview.loading.value"
        @click="overview.reload"
      >
        刷新工作台
      </button>
    </header>
    <section class="project-welcome">
      <div>
        <span class="section-kicker">当前项目 · {{ project?.key }}</span>
        <h2>每一次结果，都有来源可循。</h2>
        <p>导入 Robot 定义和运行产物，查看用例范围与失败证据。</p>
        <div class="flow-actions">
          <RouterLink class="button button-primary" :to="{ path: '/imports', query }"
            >导入测试数据 →</RouterLink
          ><RouterLink class="button button-secondary" :to="{ path: '/test-cases', query }"
            >浏览 Robot 用例</RouterLink
          >
        </div>
      </div>
      <div class="project-symbol" aria-hidden="true">
        <span>Suite</span><span>Result</span><span>Source</span>
      </div>
    </section>
    <RequestState
      :loading="overview.loading.value"
      :error="overview.error.value"
      :trace="overview.trace.value"
      @retry="overview.reload"
    />
    <template v-if="overview.data.value"
      ><dl class="flow-metrics overview-metrics">
        <div>
          <dt>已索引用例</dt>
          <dd>{{ overview.data.value.catalogCaseCount }}</dd>
        </div>
        <div>
          <dt>最近运行失败</dt>
          <dd class="result-fail">{{ overview.data.value.lastRun?.counts.fail ?? '—' }}</dd>
        </div>
        <div>
          <dt>可用文档</dt>
          <dd>{{ overview.data.value.readyDocumentCount }}</dd>
        </div>
        <div>
          <dt>文档处理失败</dt>
          <dd>{{ overview.data.value.failedDocumentCount }}</dd>
        </div>
      </dl>
      <section class="panel flow-panel">
        <div class="panel-heading">
          <h2>最近运行</h2>
          <RouterLink class="text-button" :to="{ path: '/runs', query }">全部运行 →</RouterLink>
        </div>
        <p v-if="!overview.data.value.recentRuns.length" class="flow-empty">
          尚无运行记录，导入 output.xml 后查看真实结果。
        </p>
        <ul v-else class="artifact-list">
          <li v-for="run in overview.data.value.recentRuns" :key="run.id">
            <div>
              <RouterLink class="flow-title-link" :to="{ path: '/runs/' + run.id, query }">{{
                run.name
              }}</RouterLink><small
                >{{ run.origin === 'MOCK' ? '模拟数据' : '导入结果' }} ·
                {{
                  run.startedAt ? new Date(run.startedAt).toLocaleString('zh-CN') : '未记录时间'
                }}</small
              >
            </div>
            <span class="result-inline"
              ><span class="result-pass">{{ run.counts.pass }} PASS</span
              ><b class="result-fail">{{ run.counts.fail }} FAIL</b
              ><span>{{ run.counts.skip }} SKIP</span></span
            >
          </li>
        </ul>
      </section>
      <section class="panel flow-panel">
        <h2>需要关注的文档</h2>
        <p v-if="!overview.data.value.attentionDocuments.length" class="flow-empty">
          当前没有需要关注的文档。
        </p>
        <ul v-else class="artifact-list">
          <li v-for="document in overview.data.value.attentionDocuments" :key="document.id">
            <div>
              <strong>{{ document.title }}</strong><small>{{ document.stage }}</small>
            </div>
            <span class="status-tag" :data-status="document.status">{{ document.status }}</span>
          </li>
        </ul>
        <p class="muted">文档管理与 AI 分析将在后续页面开放。</p>
      </section>
    </template>
  </div>
</template>
