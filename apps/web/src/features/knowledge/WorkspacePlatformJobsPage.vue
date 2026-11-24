<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { errorMessage } from '../../app/http';
import type { WorkspaceJob } from '../../app/workspace-contracts';
import { listPlatformJobs } from './workspace-api';

const route = useRoute();
const projectId = ref(typeof route.query.projectId === 'string' ? route.query.projectId : '');
const jobs = ref<WorkspaceJob[]>([]);
const busy = ref(false);
const error = ref('');
const page = ref(0);
const total = ref(0);
const signal = new AbortController();
let activeProject = '';
onMounted(() => search());
onBeforeUnmount(() => signal.abort());

function search() {
  activeProject = projectId.value.trim();
  void load(0);
}

async function load(p: number) {
  if (busy.value) return;
  error.value = '';
  busy.value = true;
  try {
    const result = await listPlatformJobs(activeProject, p, signal.signal);
    jobs.value = result.items;
    page.value = result.page;
    total.value = result.total;
  } catch (failure) {
    if (!signal.signal.aborted) {
      jobs.value = [];
      error.value = errorMessage(failure);
    }
  } finally {
    busy.value = false;
  }
}
const status: Record<string, string> = {
  PENDING: '待审核',
  APPROVED: '已批准',
  RETURNED: '已退回',
  QUEUED: '等待',
  PROCESSING: '处理中',
  READY: '就绪',
  FAILED: '失败',
};
const indexErrors: Record<string, string> = {
  EMBEDDING_PROVIDER_AUTH_FAILED:
    '向量服务拒绝了 API Key，请检查百炼标准 API Key 与 Base URL 是否匹配。',
  EMBEDDING_UNAVAILABLE: '向量服务没有返回有效结果，请检查向量模型配置和连通性。',
};
</script>

<template>
  <section>
    <h1 data-page-heading tabindex="-1">处理状态</h1>
    <form class="detail-panel button-row" @submit.prevent="search">
      <label
        >项目编号
        <input v-model="projectId" inputmode="numeric" pattern="[1-9][0-9]*" :disabled="busy"
      /></label>
      <button class="button button-secondary" :disabled="busy">查询</button>
    </form>
    <p v-if="error" role="alert">{{ error }}</p>
    <section class="detail-panel" :aria-busy="busy">
      <p v-if="!jobs.length">{{ busy ? '正在读取…' : '暂无记录' }}</p>
      <ul v-else class="job-list">
        <li v-for="item in jobs" :key="item.jobId">
          <strong
            >{{
              item.operation === 'REVIEW'
                ? '资料审核'
                : item.operation === 'INDEX'
                  ? '资料解析'
                  : '运行登记'
            }}
            · {{ status[item.status] ?? item.status }}</strong
          >
          <p>项目 {{ item.projectId }} · 任务 {{ item.jobId }}</p>
          <p>
            {{ item.stage }} · {{ new Date(item.updatedAt).toLocaleString('zh-CN')
            }}<span v-if="item.errorCode">
              · {{ indexErrors[item.errorCode] ?? item.errorCode }}</span
            >
          </p>
        </li>
      </ul>
      <div class="button-row">
        <button
          class="button button-secondary"
          :disabled="busy || page === 0"
          @click="load(page - 1)"
        >
          上一页
        </button>
        <button
          class="button button-secondary"
          :disabled="busy || (page + 1) * 20 >= total"
          @click="load(page + 1)"
        >
          下一页
        </button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.detail-panel {
  margin-block: 0.8rem;
}
.button-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}
.job-list {
  list-style: none;
  padding: 0;
  overflow-wrap: anywhere;
}
.job-list li {
  padding-block: 0.5rem;
}
button,
input {
  min-height: 44px;
}
</style>
