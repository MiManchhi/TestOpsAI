<script setup lang="ts">
defineProps<{ project: string; platform?: boolean }>();
</script>
<template>
  <nav class="settings-tabs" aria-label="项目设置导航">
    <RouterLink :to="platform ? '/platform/projects/' + project : '/p/' + project + '/settings'"
      >基本信息</RouterLink
    >
    <RouterLink v-if="!platform" :to="'/p/' + project + '/settings/members'">成员</RouterLink>
    <RouterLink v-if="!platform" :to="'/p/' + project + '/settings/bases'">验收依据</RouterLink>
    <RouterLink
      :to="
        platform
          ? '/platform/projects/' + project + '/owner'
          : '/p/' + project + '/settings/transfer'
      "
      >负责人交接</RouterLink
    >
  </nav>
</template>
<style scoped>
.settings-tabs {
  display: flex;
  flex-wrap: wrap;
  border-bottom: 1px solid var(--border);
  gap: 18px;
  margin: 14px 0 19px;
}
.settings-tabs a {
  padding: 9px 2px;
  color: var(--muted);
  border-bottom: 2px solid transparent;
}
.settings-tabs a.router-link-exact-active {
  border-color: var(--primary);
  color: var(--primary);
  font-weight: 650;
}
</style>
