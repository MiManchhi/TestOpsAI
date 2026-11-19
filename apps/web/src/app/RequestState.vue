<script setup lang="ts">
defineProps<{ loading?: boolean; error?: string; trace?: string }>();
defineEmits<{ retry: [] }>();
</script>
<template>
  <div v-if="loading" class="flow-state" role="status">
    <span class="spinner" />正在读取，请稍候…
  </div>
  <div v-else-if="error" class="flow-state flow-error" role="alert">
    <strong>暂时无法完成请求</strong>
    <p>{{ error }}</p>
    <small v-if="trace">请求编号：{{ trace }}</small>
    <button class="button button-secondary" type="button" @click="$emit('retry')">重新读取</button>
  </div>
</template>
