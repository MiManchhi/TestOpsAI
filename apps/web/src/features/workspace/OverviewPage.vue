<script setup lang="ts">
import { projects } from '../projects/context';
import { auth } from '../auth/session';
import HealthStatus from '../health/HealthStatus.vue';
import AppIcon from '../../app/AppIcon.vue';
const project = projects.current;
</script>
<template>
  <div v-if="project" class="overview-page">
    <div class="page-heading-row">
      <div>
        <p class="section-kicker">METER TESTING / 测试协作</p>
        <h1 data-page-heading tabindex="-1">工作台</h1>
        <p class="muted">{{ auth.state.user?.username }}，从当前项目的测试事实开始。</p>
      </div>
    </div>
    <section class="project-welcome">
      <div>
        <span class="section-kicker">当前项目 · {{ project.key }}</span>
        <h2>{{ project.name }}</h2>
        <p>{{ project.description || '查看套件、用例定义与来源，明确本次测试范围。' }}</p>
        <RouterLink
          class="button button-primary"
          :to="{ path: '/test-cases', query: { projectId: project.id } }"
          >浏览 Robot 用例<AppIcon name="arrow"
        /></RouterLink>
      </div>
      <div class="project-symbol" aria-hidden="true">
        <span>Suite</span><span>Case</span><span>Source</span>
      </div>
    </section>
    <div class="overview-details">
      <section class="panel identity-panel">
        <div class="panel-heading">
          <div>
            <p class="section-kicker">PROJECT CONTEXT</p>
            <h2>项目上下文</h2>
          </div>
          <AppIcon name="shield" />
        </div>
        <dl class="account-details">
          <div>
            <dt>项目标识</dt>
            <dd>{{ project.key }}</dd>
          </div>
          <div>
            <dt>我的角色</dt>
            <dd>{{ { OWNER: '项目所有者', EDITOR: '编辑者', VIEWER: '查看者' }[project.role] }}</dd>
          </div>
          <div>
            <dt>当前身份</dt>
            <dd>{{ auth.state.user?.username }}</dd>
          </div>
        </dl>
        <RouterLink
          class="text-button"
          :to="{ path: '/settings/project', query: { projectId: project.id } }"
          >查看项目资料<AppIcon name="arrow"
        /></RouterLink>
      </section>
      <HealthStatus />
    </div>
  </div>
</template>
