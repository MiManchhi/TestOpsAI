<script setup lang="ts">
import { nextTick, onScopeDispose, ref, watch } from 'vue';
import { projects } from '../projects/context';
import { getCitation, getDocument } from './api';
import { useResource } from '../../app/resource';
import RequestState from '../../app/RequestState.vue';
const props = defineProps<{ citationId: string }>();
const emit = defineEmits<{ close: [] }>();
const dialog = ref<HTMLDialogElement>();
const source = useResource(
  () => props.citationId,
  async (signal) => {
    if (!props.citationId) return null;
    const citation = await getCitation(projects.state.selectedId, props.citationId, signal);
    const metadata =
      citation.kind === 'DOCUMENT'
        ? await getDocument(projects.state.selectedId, citation.resourceId, signal)
        : null;
    return { citation, metadata };
  },
);
watch(
  () => props.citationId,
  async (id) => {
    await nextTick();
    if (id) {
      if (!dialog.value?.open) dialog.value?.showModal();
    } else dialog.value?.close();
  },
  { immediate: true },
);
watch(
  () => projects.state.selectedId,
  () => {
    dialog.value?.close();
    emit('close');
  },
  { flush: 'sync' },
);
onScopeDispose(() => dialog.value?.close());
</script>
<template>
  <dialog
    ref="dialog"
    class="flow-dialog citation-dialog"
    aria-labelledby="citation-title"
    @close="emit('close')"
  >
    <div class="panel-heading">
      <h2 id="citation-title">来源与依据</h2>
      <button class="text-button" @click="emit('close')">关闭来源</button>
    </div>
    <RequestState
      :loading="source.loading.value"
      :error="source.error.value"
      :trace="source.trace.value"
      @retry="source.reload"
    />
    <template v-if="source.data.value"
      ><p class="section-kicker">
        {{
          { DOCUMENT: '文档原文', TEST_CASE: '用例定义', TEST_RESULT: '运行结果' }[
            source.data.value.citation.kind
          ]
        }}
      </p>
      <h3>{{ source.data.value.citation.title }}</h3>
      <p class="muted">引用版本：{{ source.data.value.citation.version }}</p>
      <dl class="metadata-list">
        <div v-if="source.data.value.citation.locator.page">
          <dt>页码</dt>
          <dd>{{ source.data.value.citation.locator.page }}</dd>
        </div>
        <div v-if="source.data.value.citation.locator.line">
          <dt>行号</dt>
          <dd>{{ source.data.value.citation.locator.line }}</dd>
        </div>
        <div v-if="source.data.value.citation.locator.section">
          <dt>章节</dt>
          <dd>{{ source.data.value.citation.locator.section }}</dd>
        </div>
      </dl>
      <blockquote class="citation-excerpt">{{ source.data.value.citation.excerpt }}</blockquote>
      <RouterLink
        v-if="source.data.value.metadata"
        class="button button-primary"
        :to="{
          path: `/knowledge/${source.data.value.metadata.knowledgeBaseId}/documents/${source.data.value.metadata.id}`,
          query: { projectId: projects.state.selectedId, citationId },
        }"
        @click="emit('close')"
        >阅读对应版本原文 →</RouterLink
      ><RouterLink
        v-else-if="source.data.value.citation.kind === 'TEST_CASE'"
        class="button button-primary"
        :to="{
          path: '/test-cases/' + source.data.value.citation.resourceId,
          query: { projectId: projects.state.selectedId },
        }"
        @click="emit('close')"
        >查看用例定义 →</RouterLink
      ></template
    >
  </dialog>
</template>
