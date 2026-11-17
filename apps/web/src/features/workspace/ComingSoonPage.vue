<script setup lang="ts">
import { computed } from 'vue';
import AppIcon from '../../app/AppIcon.vue';
const props = defineProps<{ kind: 'knowledge' | 'test-cases' }>();
const isKnowledge = computed(() => props.kind === 'knowledge');
const title = computed(() => (isKnowledge.value ? '知识库' : '测试用例'));
</script>
<template>
  <div>
    <div class="page-heading-row">
      <div>
        <p class="section-kicker">WORKSPACE / 协作空间</p>
        <h1 data-page-heading tabindex="-1">{{ title }}</h1>
        <p class="muted">
          {{ isKnowledge ? '让测试经验有处沉淀，随时查阅。' : '让测试用例有序组织，协作更清楚。' }}
        </p>
      </div>
      <span class="status-badge">待开放</span>
    </div>
    <section class="panel empty-state" :aria-label="`${title}开放说明`">
      <span class="empty-illustration"><AppIcon :name="isKnowledge ? 'book' : 'check'" /></span
      ><span class="section-kicker">COMING SOON</span>
      <h2>{{ title }}正在建设中</h2>
      <p>
        {{
          isKnowledge
            ? '后续将支持整理与查找团队的测试知识。'
            : '后续将支持管理与维护团队的测试用例。'
        }}<br />功能开放后，你可以从这里开始使用。
      </p>
      <RouterLink to="/overview" class="button button-primary"
        >返回工作台 <AppIcon name="arrow"
      /></RouterLink>
    </section>
  </div>
</template>
