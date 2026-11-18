<script setup lang="ts">
import { projects } from './context';
const project = projects.current;
const roles = { OWNER: '项目所有者', EDITOR: '编辑者', VIEWER: '查看者' };
</script>
<template>
  <div v-if="project" class="project-page">
    <div class="page-heading-row">
      <div>
        <p class="section-kicker">PROJECT / 项目</p>
        <h1 data-page-heading tabindex="-1">项目与成员</h1>
        <p class="muted">当前项目的身份、说明与访问角色。</p>
      </div>
    </div>
    <section class="detail-panel project-information">
      <span class="project-emblem" aria-hidden="true">{{ project.key.slice(0, 2) }}</span>
      <h2>{{ project.name }}</h2>
      <p class="preserve-text muted">{{ project.description || '暂无项目说明。' }}</p>
      <dl class="metadata-list">
        <div>
          <dt>项目标识</dt>
          <dd>{{ project.key }}</dd>
        </div>
        <div>
          <dt>项目 ID</dt>
          <dd>{{ project.id }}</dd>
        </div>
        <div>
          <dt>我的角色</dt>
          <dd>{{ roles[project.role] }}</dd>
        </div>
        <div>
          <dt>创建时间</dt>
          <dd>{{ new Date(project.createdAt).toLocaleString('zh-CN') }}</dd>
        </div>
      </dl>
      <RouterLink
        class="button button-primary"
        :to="{ path: '/test-cases', query: { projectId: project.id } }"
        >查看项目用例</RouterLink
      >
    </section>
  </div>
</template>
