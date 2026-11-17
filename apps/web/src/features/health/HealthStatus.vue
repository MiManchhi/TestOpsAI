<script setup lang="ts">
import { ref } from 'vue';
import { getServerHealth } from './api';

const status = ref('尚未检查');
const pending = ref(false);

async function checkHealth() {
  pending.value = true;
  try {
    await getServerHealth();
    status.value = '业务服务可用';
  } catch {
    status.value = '暂时无法连接业务服务，请稍后重试';
  } finally {
    pending.value = false;
  }
}
</script>

<template>
  <section aria-labelledby="service-heading">
    <h2 id="service-heading">服务连接</h2>
    <p role="status" aria-live="polite">{{ status }}</p>
    <button :disabled="pending" @click="checkHealth">
      {{ pending ? '检查中…' : '检查连接' }}
    </button>
  </section>
</template>
