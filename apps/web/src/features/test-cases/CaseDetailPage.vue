<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ApiError, errorMessage, requestId } from '../../app/http';
import { projects } from '../projects/context';
import { getTestCase, type TestCase } from './api';
import { caseCatalog } from './catalog';
const route = useRoute();
const item = ref<TestCase | null>(null);
const loading = ref(false),
  error = ref(''),
  errorId = ref('');
let controller: AbortController | undefined;
let version = 0;
async function load() {
  controller?.abort();
  controller = new AbortController();
  const signal = controller.signal,
    generation = ++version;
  item.value = null;
  error.value = '';
  errorId.value = '';
  loading.value = true;
  const caseId = route.params.caseId;
  if (!projects.state.selectedId || typeof caseId !== 'string') {
    loading.value = false;
    return;
  }
  try {
    const result = await getTestCase(projects.state.selectedId, caseId, signal);
    if (signal.aborted || generation !== version) return;
    item.value = result;
  } catch (failure) {
    if (signal.aborted || generation !== version) return;
    error.value = errorMessage(failure);
    errorId.value = requestId(failure);
    if (failure instanceof ApiError && (failure.status === 403 || failure.status === 404))
      caseCatalog.setProject('');
  } finally {
    if (generation === version) loading.value = false;
  }
}
watch(
  () => [projects.state.selectedId, route.params.caseId],
  () => {
    void load();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  version++;
  controller?.abort();
});
</script>
<template>
  <div class="case-detail-page">
    <RouterLink
      class="back-link"
      :to="{ path: '/test-cases', query: { projectId: projects.state.selectedId } }"
      >← 返回 Robot 用例</RouterLink
    >
    <section v-if="loading" class="panel context-state" role="status">
      <span class="spinner" />正在读取用例详情…
    </section>
    <section v-else-if="error" class="panel context-state">
      <h1 data-page-heading tabindex="-1">无法读取用例</h1>
      <p role="alert">{{ error }}</p>
      <small v-if="errorId">请求编号：{{ errorId }}</small
      ><button class="button button-secondary" @click="load">重新读取</button>
    </section>
    <template v-else-if="item">
      <div class="page-heading-row">
        <div>
          <p class="section-kicker">CASE DEFINITION / 用例定义</p>
          <h1 data-page-heading tabindex="-1">{{ item.title }}</h1>
          <p class="muted">
            {{ item.module || '未分类' }} ·
            {{ item.sourceType === 'ROBOT' ? 'Robot 定义' : '人工用例' }}
          </p>
        </div>
        <span class="definition-version">v{{ item.version }}</span>
      </div>
      <p v-if="item.sourceType === 'ROBOT'" class="definition-notice">
        Robot 来源只读。需要变更时，请维护源文件后重新导入。
      </p>
      <div class="detail-grid">
        <div class="detail-main">
          <section class="detail-panel">
            <h2>用例说明</h2>
            <p class="preserve-text">{{ item.documentation || '暂无说明。' }}</p>
            <h3>预期结果</h3>
            <p class="preserve-text">{{ item.expectedResult || '未提供预期结果。' }}</p>
          </section>
          <section class="detail-panel">
            <div class="catalog-panel-heading">
              <h2>关键字步骤</h2>
              <span class="muted small-text">{{ item.steps.length }} 步</span>
            </div>
            <ol v-if="item.steps.length" class="keyword-steps">
              <li v-for="(step, index) in item.steps" :key="`${step.line}-${index}`">
                <span class="step-line">L{{ step.line }}</span>
                <div>
                  <code>{{ step.keyword }}</code>
                  <ul v-if="step.arguments.length" class="argument-list">
                    <li v-for="(argument, argumentIndex) in step.arguments" :key="argumentIndex">
                      <code>{{ argument }}</code>
                    </li>
                  </ul>
                </div>
              </li>
            </ol>
            <p v-else class="muted">未提供结构化步骤。</p>
          </section>
        </div>
        <aside class="detail-metadata">
          <section class="detail-panel">
            <h2>定义信息</h2>
            <dl class="metadata-list">
              <div>
                <dt>用例 ID</dt>
                <dd>{{ item.id }}</dd>
              </div>
              <div>
                <dt>定义状态</dt>
                <dd>{{ item.lifecycleStatus === 'ACTIVE' ? '有效' : '已归档' }}</dd>
              </div>
              <div>
                <dt>优先级</dt>
                <dd>{{ item.priority }}</dd>
              </div>
              <div>
                <dt>更新时间</dt>
                <dd>{{ new Date(item.updatedAt).toLocaleString('zh-CN') }}</dd>
              </div>
            </dl>
            <h3>精确标签</h3>
            <div class="tag-list">
              <span v-for="tag in item.tags" :key="tag" class="case-tag">{{ tag }}</span><span
                v-if="!item.tags.length"
                class="muted"
                >无标签</span
              >
            </div>
          </section>
          <section class="detail-panel">
            <h2>来源与版本</h2>
            <template v-if="item.sourceRef"
              ><p class="source-path">
                <code>{{ item.sourceRef.path }}</code><span>第 {{ item.sourceRef.line }} 行</span>
              </p>
              <h3>内容版本摘要</h3>
              <code class="hash-value">{{ item.sourceRef.revision }}</code></template
            >
            <p v-else class="muted">人工维护的定义，无 Robot 源文件。</p>
            <details class="stable-key">
              <summary>稳定标识</summary>
              <code class="hash-value">{{ item.stableKey }}</code>
            </details>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>
