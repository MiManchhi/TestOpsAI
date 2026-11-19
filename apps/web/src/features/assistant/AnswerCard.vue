<script setup lang="ts">
import type { Answer, MessageContext } from '../../app/contracts';
import { projects } from '../projects/context';
import SafeMarkdown from './SafeMarkdown.vue';
defineProps<{ answer: Answer; context?: MessageContext | null }>();
defineEmits<{ citation: [id: string] }>();
</script>
<template>
  <article class="answer-card">
    <div class="panel-heading">
      <span class="section-kicker">AI / 分析建议</span
      ><span class="status-tag" :data-status="answer.evidenceStatus">{{
        { SUFFICIENT: '依据充分', PARTIAL: '部分依据', NONE: '缺少依据' }[answer.evidenceStatus]
      }}</span>
    </div>
    <h2>{{ answer.summary }}</h2>
    <p v-if="answer.evidenceStatus !== 'SUFFICIENT'" class="notice">
      {{
        answer.evidenceStatus === 'NONE'
          ? '当前缺少可核实的资料依据，请补充资料或缩小问题范围。'
          : '部分结论仍需验证，请结合来源与原始结果核对。'
      }}
    </p>
    <SafeMarkdown :text="answer.bodyMarkdown" />
    <ul v-if="answer.warnings.length" class="answer-warnings">
      <li v-for="(warning, index) in answer.warnings" :key="index">{{ warning }}</li>
    </ul>
    <section v-if="answer.citationIds.length" class="answer-sources" aria-label="回答引用">
      <h3>依据与来源</h3>
      <div class="flow-actions">
        <button
          v-for="(id, index) in answer.citationIds"
          :key="id"
          class="citation-button"
          @click="$emit('citation', id)"
        >
          [{{ index + 1 }}] 阅读来源
        </button>
      </div>
    </section>
    <details v-if="answer.caseIds.length" class="answer-related">
      <summary>相关用例 · {{ answer.caseIds.length }} 条</summary>
      <div class="flow-actions">
        <RouterLink
          v-for="id in answer.caseIds"
          :key="id"
          class="text-button"
          :to="{ path: '/test-cases/' + id, query: { projectId: projects.state.selectedId } }"
          >查看用例 #{{ id }} →</RouterLink
        >
      </div>
    </details>
    <RouterLink
      v-if="answer.resultIds.length && context?.runId"
      class="text-button"
      :to="{ path: '/runs/' + context.runId, query: { projectId: projects.state.selectedId } }"
      >查看本次运行中的结果 · {{ answer.resultIds.length }} 条关联结果 →</RouterLink
    >
    <div v-if="answer.draftId" class="draft-callout">
      <div>
        <strong>建议草稿已准备好</strong>
        <p>检查字段与依据，选择要保存的人工用例。</p>
      </div>
      <RouterLink
        class="button button-primary"
        :to="{ path: '/drafts/' + answer.draftId, query: { projectId: projects.state.selectedId } }"
        >检查并编辑草稿 →</RouterLink
      >
    </div>
  </article>
</template>
