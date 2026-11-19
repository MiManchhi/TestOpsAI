<script setup lang="ts">
defineProps<{ page: number; size: number; total: number; busy?: boolean }>();
defineEmits<{ change: [page: number] }>();
</script>
<template>
  <nav class="flow-pagination" aria-label="分页">
    <span
      >共 {{ total }} 条 · 第 {{ page + 1 }} / {{ Math.max(1, Math.ceil(total / size)) }} 页</span
    >
    <div>
      <button
        class="button button-secondary"
        type="button"
        :disabled="busy || page === 0"
        @click="$emit('change', page - 1)"
      >
        上一页
      </button>
      <button
        class="button button-secondary"
        type="button"
        :disabled="busy || (page + 1) * size >= total"
        @click="$emit('change', page + 1)"
      >
        下一页
      </button>
    </div>
  </nav>
</template>
