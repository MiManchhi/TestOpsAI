<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { projects } from '../projects/context';
import {
  getCitation,
  getDocument,
  getDocumentContent,
  getKnowledgeBase,
  documentStage,
  documentStatus,
} from './api';
import { useResource } from '../../app/resource';
import RequestState from '../../app/RequestState.vue';
import type { Citation, Document, DocumentBlock } from '../../app/contracts';
const route = useRoute();
const kbId = computed(() => String(route.params.kbId ?? ''));
const documentId = computed(() => String(route.params.documentId ?? ''));
const citationId = computed(() =>
  typeof route.query.citationId === 'string' ? route.query.citationId : '',
);
const cursor = ref(''),
  nextCursor = ref<string | null>(null),
  localError = ref('');
const metadata = ref<Document | null>(null),
  citation = ref<Citation | null>(null),
  blocks = ref<DocumentBlock[]>([]);
const seen = new Set<string>();
const scope = computed(
  () => `${projects.state.selectedId}:${kbId.value}:${documentId.value}:${citationId.value}`,
);
watch(
  scope,
  () => {
    cursor.value = '';
    nextCursor.value = null;
    metadata.value = citation.value = null;
    blocks.value = [];
    localError.value = '';
    seen.clear();
  },
  { flush: 'sync' },
);
const content = useResource(
  () => `${scope.value}:${cursor.value}`,
  async (signal) => {
    const pid = projects.state.selectedId;
    await getKnowledgeBase(pid, kbId.value, signal);
    const meta = await getDocument(pid, documentId.value, signal, kbId.value);
    const source = citationId.value ? await getCitation(pid, citationId.value, signal) : null;
    if (source && (source.kind !== 'DOCUMENT' || source.resourceId !== documentId.value))
      throw new Error('引用与当前文档不匹配。');
    if (meta.status !== 'READY') return { meta, source, page: null };
    const page = await getDocumentContent(pid, kbId.value, documentId.value, cursor.value, signal);
    if (
      page.document.contentHash !== meta.contentHash ||
      page.document.versionLabel !== meta.versionLabel
    )
      throw new Error('文档版本发生变化，请重新读取。');
    return { meta, source, page };
  },
);
watch(content.data, (value) => {
  if (!value) return;
  metadata.value = value.meta;
  citation.value = value.source;
  if (!value.page) {
    blocks.value = [];
    nextCursor.value = null;
    return;
  }
  if (!cursor.value) blocks.value = [];
  const existing = new Set(blocks.value.map((block) => block.id));
  if (value.page.blocks.some((block) => existing.has(block.id))) {
    localError.value = '文档分页出现重复内容，请重新读取。';
    return;
  }
  blocks.value.push(...value.page.blocks);
  nextCursor.value = value.page.nextCursor;
});
watch(content.error, (value) => {
  if (value) {
    metadata.value = citation.value = null;
    blocks.value = [];
    nextCursor.value = null;
  }
});
const query = computed(() => ({ projectId: projects.state.selectedId }));
const highlighted = computed(() => {
  const locator = citation.value?.locator;
  if (!locator) return -1;
  return blocks.value.findIndex((block) =>
    locator.page
      ? block.page === locator.page
      : locator.line
        ? block.startLine !== null &&
          locator.line >= block.startLine &&
          locator.line < block.startLine + block.text.split('\n').length
        : locator.section
          ? block.heading === locator.section
          : false,
  );
});
async function locate(index = highlighted.value) {
  await nextTick();
  if (index >= 0)
    document.getElementById(`document-block-${index}`)?.scrollIntoView({ block: 'center' });
}
function more() {
  if (!nextCursor.value) return;
  if (seen.has(nextCursor.value) || nextCursor.value === cursor.value) {
    localError.value = '文档分页游标重复，请重新读取。';
    return;
  }
  seen.add(nextCursor.value);
  cursor.value = nextCursor.value;
}
function reload() {
  blocks.value = [];
  metadata.value = citation.value = null;
  nextCursor.value = null;
  localError.value = '';
  seen.clear();
  if (cursor.value) cursor.value = '';
  else void content.reload();
}
</script>
<template>
  <div class="flow-page">
    <RouterLink class="text-button" :to="{ path: '/knowledge/' + kbId, query }"
      >← 返回知识库</RouterLink
    >
    <header class="page-heading-row">
      <div>
        <p class="section-kicker">DOCUMENT / 版本与原文</p>
        <h1 data-page-heading tabindex="-1">{{ metadata?.title ?? '文档阅读' }}</h1>
        <p v-if="metadata" class="muted">{{ metadata.versionLabel }} · {{ metadata.mimeType }}</p>
      </div>
      <RouterLink
        v-if="metadata?.status === 'READY'"
        class="button button-primary"
        :to="{ path: '/assistant', query: { ...query, kbId, documentId } }"
        >问这篇文档 →</RouterLink
      >
    </header>
    <RequestState
      :loading="content.loading.value && !blocks.length"
      :error="content.error.value"
      :trace="content.trace.value"
      @retry="reload"
    />
    <p v-if="localError" class="flow-error" role="alert">
      {{ localError }}<button class="text-button" @click="reload">从头读取</button>
    </p>
    <section v-if="citation" class="panel flow-panel">
      <div class="panel-heading">
        <h2>引用定位</h2>
        <button class="text-button" :disabled="highlighted < 0" @click="locate()">
          定位到原文
        </button>
      </div>
      <p>{{ citation.title }} · 引用版本 {{ citation.version }}</p>
      <blockquote class="citation-excerpt">{{ citation.excerpt }}</blockquote>
      <p v-if="highlighted < 0" class="muted">目标段落尚未加载，可继续加载文档查找引用位置。</p>
    </section>
    <section v-if="metadata && metadata.status !== 'READY'" class="panel flow-panel">
      <h2>{{ documentStatus[metadata.status] }}</h2>
      <p>{{ documentStage[metadata.stage] }}。文档就绪后可阅读与提问。</p>
      <button class="button button-secondary" @click="reload">刷新文档状态</button>
    </section>
    <div v-if="metadata?.status === 'READY'" class="document-reader">
      <aside class="panel document-outline" aria-label="文档目录">
        <h2>本页目录</h2>
        <button
          v-for="(block, index) in blocks"
          :key="block.id"
          class="text-button"
          @click="locate(index)"
        >
          {{
            block.heading ||
            (block.page
              ? '第 ' + block.page + ' 页'
              : '第 ' + (block.startLine ?? index + 1) + ' 行')
          }}
        </button>
        <p v-if="!blocks.length" class="muted">暂无正文段落。</p>
      </aside>
      <article class="panel document-body">
        <section
          v-for="(block, index) in blocks"
          :id="'document-block-' + index"
          :key="block.id"
          :class="{ 'cited-block': index === highlighted }"
        >
          <p class="section-kicker">
            {{
              block.page
                ? '第 ' + block.page + ' 页'
                : block.startLine
                  ? '第 ' + block.startLine + ' 行起'
                  : '原文段落'
            }}
          </p>
          <h2 v-if="block.heading">{{ block.heading }}</h2>
          <p class="preserve-text">{{ block.text }}</p>
        </section>
        <p v-if="!blocks.length" class="flow-empty">没有可展示的正文。</p>
        <button
          v-if="nextCursor"
          class="button button-secondary"
          :disabled="content.loading.value || !!localError"
          @click="more"
        >
          {{ content.loading.value ? '正在读取…' : '继续加载正文' }}
        </button>
        <p v-else class="muted">已加载全部正文。</p>
      </article>
    </div>
  </div>
</template>
