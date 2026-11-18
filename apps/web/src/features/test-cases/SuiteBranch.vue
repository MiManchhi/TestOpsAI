<script setup lang="ts">
import { computed, ref } from 'vue';
import type { Suite } from './api';
const props = defineProps<{ suite: Suite; suites: ReadonlyArray<Suite>; selectedId?: string }>();
const emit = defineEmits<{ select: [id: string] }>();
const expanded = ref(true);
const children = computed(() => props.suites.filter((item) => item.parentId === props.suite.id));
</script>
<template>
  <li>
    <div class="suite-row">
      <button
        v-if="children.length"
        class="suite-expand"
        :aria-expanded="expanded"
        :aria-label="`${expanded ? '收起' : '展开'}${suite.name}`"
        @click="expanded = !expanded"
      >
        {{ expanded ? '−' : '+' }}</button><span v-else class="suite-leaf" aria-hidden="true"
        >·</span
      >
      <button
        class="suite-choice"
        :class="{ selected: selectedId === suite.id }"
        :aria-pressed="selectedId === suite.id"
        :title="suite.sourcePath"
        @click="emit('select', suite.id)"
      >
        <span>{{ suite.name }}</span><span class="suite-count">{{ suite.caseCount }}</span>
      </button>
    </div>
    <ul v-if="children.length && expanded" class="suite-children">
      <SuiteBranch
        v-for="child in children"
        :key="child.id"
        :suite="child"
        :suites="suites"
        :selected-id="selectedId"
        @select="emit('select', $event)"
      />
    </ul>
  </li>
</template>
