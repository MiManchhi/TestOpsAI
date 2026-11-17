<script setup lang="ts">
import AppIcon from '../../app/AppIcon.vue';
import { auth } from '../auth/session';
import HealthStatus from '../health/HealthStatus.vue';
const date = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long',
}).format(new Date());
const joinedAt = () =>
  auth.state.user
    ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium' }).format(
        new Date(auth.state.user.createdAt),
      )
    : '—';
</script>

<template>
  <div class="overview-page">
    <div class="page-heading-row">
      <div>
        <p class="section-kicker">WORKSPACE / 工作台</p>
        <h1 data-page-heading tabindex="-1">工作台概览</h1>
        <p class="muted">欢迎回来，{{ auth.state.user?.username }}。从这里开始今天的测试协作。</p>
      </div>
      <span class="date-label">{{ date }}</span>
    </div>
    <section class="welcome-banner" aria-labelledby="welcome-title">
      <div class="welcome-copy">
        <span class="banner-kicker">有序积累，可靠交付</span>
        <h2 id="welcome-title">把经验留下，把协作连接起来。</h2>
        <p>在一个空间中，逐步建立团队的测试知识与协作方式。</p>
        <a class="banner-link" href="#workspace-entries">了解工作台入口 <AppIcon name="arrow" /></a>
      </div>
      <div class="banner-art" aria-hidden="true">
        <div class="art-ring ring-one" />
        <div class="art-ring ring-two" />
        <div class="art-tile tile-book"><AppIcon name="book" /></div>
        <div class="art-tile tile-check"><AppIcon name="check" /></div>
        <span class="art-spark spark-one" /><span class="art-spark spark-two" />
      </div>
    </section>
    <div class="overview-details">
      <section class="panel identity-panel" aria-labelledby="account-heading">
        <div class="panel-heading">
          <div>
            <p class="section-kicker">ACCOUNT</p>
            <h2 id="account-heading">当前账户</h2>
          </div>
          <span class="icon-surface"><AppIcon name="user" /></span>
        </div>
        <dl class="account-details">
          <div>
            <dt>用户名</dt>
            <dd>{{ auth.state.user?.username }}</dd>
          </div>
          <div>
            <dt>账户角色</dt>
            <dd class="role-list">
              <span v-for="role in auth.state.user?.roles" :key="role" class="role-badge">{{
                role
              }}</span><span v-if="!auth.state.user?.roles.length">暂无角色</span>
            </dd>
          </div>
          <div>
            <dt>创建时间</dt>
            <dd>{{ joinedAt() }}</dd>
          </div>
        </dl>
      </section>
      <HealthStatus />
    </div>
    <section id="workspace-entries" class="workspace-entries" aria-labelledby="entries-heading">
      <div class="section-heading">
        <div>
          <p class="section-kicker">EXPLORE</p>
          <h2 id="entries-heading">你的协作入口</h2>
        </div>
        <span class="muted small-text">能力逐步开放，经验持续积累</span>
      </div>
      <div class="entry-grid">
        <RouterLink to="/knowledge" class="entry-card"
          ><span class="entry-icon"><AppIcon name="book" /></span
          ><span class="entry-copy"
            ><span class="entry-title">知识库 <span class="status-badge">待开放</span></span
            ><span class="muted">集中整理测试经验，让知识便于查找与复用。</span
            ><span class="entry-link"
              >查看开放说明 <AppIcon name="arrow" /></span></span></RouterLink
        ><RouterLink to="/test-cases" class="entry-card"
          ><span class="entry-icon"><AppIcon name="check" /></span
          ><span class="entry-copy"
            ><span class="entry-title">测试用例 <span class="status-badge">待开放</span></span
            ><span class="muted">有序组织测试用例，让测试过程更清楚。</span
            ><span class="entry-link">查看开放说明 <AppIcon name="arrow" /></span></span
        ></RouterLink>
      </div>
    </section>
  </div>
</template>
