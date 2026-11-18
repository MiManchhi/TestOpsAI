<script setup lang="ts">
import { ref, watch } from 'vue';
import { projects } from '../projects/context';
import { useResource } from '../../app/resource';
import RequestState from '../../app/RequestState.vue';
import PageControls from '../../app/PageControls.vue';
import { listRuns, type RunStatus } from './api';
const page = ref(0);
const status = ref<RunStatus>('');
watch([status, () => projects.state.selectedId], () => {
  page.value = 0;
});
const runs = useResource(
  () => `${page.value}:${status.value}`,
  (signal) => listRuns(projects.state.selectedId, page.value, status.value, signal),
);
</script>
<template>
  <div class="flow-page">
    <header class="page-heading-row">
      <div>
        <p class="section-kicker">RUNS / 结果与证据</p>
        <h1 data-page-heading tabindex="-1">运行记录</h1>
        <p class="muted">来自已导入的 Robot 运行产物，保留当次快照与结果。</p>
      </div>
      <RouterLink
        class="button button-primary"
        :to="{ path: '/imports', query: { projectId: projects.state.selectedId } }"
        >导入运行结果</RouterLink
      >
    </header>
    <section class="panel flow-panel">
      <div class="flow-toolbar">
        <label
          >完整性<select v-model="status" aria-label="完整性">
            <option value="">全部运行</option>
            <option value="COMPLETED">完整</option>
            <option value="INCOMPLETE">不完整</option>
          </select></label
        ><button class="text-button" :disabled="runs.loading.value" @click="runs.reload">
          刷新记录
        </button>
      </div>
      <RequestState
        :loading="runs.loading.value"
        :error="runs.error.value"
        :trace="runs.trace.value"
        @retry="runs.reload"
      />
      <template v-if="runs.data.value"
        ><p v-if="!runs.data.value.items.length" class="flow-empty">
          {{
            status
              ? '没有符合条件的运行，试试切换完整性筛选。'
              : '暂无运行记录，导入 output.xml 后即可查看。'
          }}
        </p>
        <div v-else class="flow-table-scroll">
          <table class="flow-table">
            <thead>
              <tr>
                <th>运行 / 来源</th>
                <th>开始时间</th>
                <th>结果分布</th>
                <th>完整性</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="run in runs.data.value.items" :key="run.id">
                <td>
                  <RouterLink
                    class="flow-title-link"
                    :to="{
                      path: '/runs/' + run.id,
                      query: { projectId: projects.state.selectedId },
                    }"
                    >{{ run.name }}</RouterLink><small
                    >{{ run.origin === 'MOCK' ? '模拟数据' : '导入结果' }} · #{{ run.id }}</small
                  >
                </td>
                <td>
                  {{ run.startedAt ? new Date(run.startedAt).toLocaleString('zh-CN') : '未记录' }}
                </td>
                <td>
                  <span class="result-inline"
                    ><b class="result-pass">{{ run.counts.pass }} PASS</b
                    ><b class="result-fail">{{ run.counts.fail }} FAIL</b
                    ><span>{{ run.counts.skip }} SKIP</span></span
                  >
                </td>
                <td>
                  <span class="status-tag" :data-status="run.status">{{
                    run.status === 'COMPLETED' ? '完整' : '不完整'
                  }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PageControls
          :page="page"
          :size="20"
          :total="runs.data.value.total"
          :busy="runs.loading.value"
          @change="page = $event"
      /></template>
    </section>
  </div>
</template>
