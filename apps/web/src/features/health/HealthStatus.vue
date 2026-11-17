<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { getServerHealth } from './api';
import AppIcon from '../../app/AppIcon.vue';

const status = ref('尚未检查');
const pending = ref(false);
const available = ref<boolean | null>(null);
const checkedAt = ref('');
let disposed = false;

async function checkHealth() {
  pending.value = true;
  try {
    await getServerHealth();
    if (disposed) return;
    available.value = true;
    status.value = '业务服务可用';
  } catch {
    if (disposed) return;
    available.value = false;
    status.value = '暂时无法连接业务服务，请稍后重试';
  } finally {
    pending.value = false;
    if (!disposed) checkedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  }
}
onMounted(checkHealth);
onBeforeUnmount(() => {
  disposed = true;
});
</script>

<template>
  <section class="panel health-panel" aria-labelledby="service-heading" :aria-busy="pending">
    <div class="panel-heading">
      <div>
        <p class="section-kicker">CONNECTION</p>
        <h2 id="service-heading">服务连接</h2>
      </div>
      <span class="icon-surface"><AppIcon name="activity" /></span>
    </div>
    <p
      class="health-summary"
      :class="{ 'is-up': available === true, 'is-down': available === false }"
      role="status"
      aria-live="polite"
    >
      <span class="small-dot" />{{
        pending
          ? '正在检查连接…'
          : available === true
            ? '业务服务可用'
            : available === false
              ? '连接暂不可用'
              : status
      }}
    </p>
    <p class="health-description">
      {{
        available === false
          ? '暂时无法连接业务服务，请稍后重试。'
          : '检查工作台与业务服务的连接情况。'
      }}
    </p>
    <div class="health-footer">
      <small>{{ checkedAt ? `最近检查 ${checkedAt}` : '尚未检查' }}</small><button
        class="text-button"
        :disabled="pending"
        @click="checkHealth"
      >
        <AppIcon name="refresh" />{{ pending ? '检查中…' : '检查连接' }}
      </button>
    </div>
  </section>
</template>
