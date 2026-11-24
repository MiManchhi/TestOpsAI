<script setup lang="ts">
import { auth } from '../auth/session';
</script>
<template>
  <div>
    <div class="page-heading-row">
      <div>
        <h1 data-page-heading tabindex="-1">我的账户</h1>
        <p class="muted">当前标签页的登录身份。</p>
      </div>
    </div>
    <section class="detail-panel project-information">
      <h2>{{ auth.state.user?.username }}</h2>
      <dl class="metadata-list">
        <div>
          <dt>账户角色</dt>
          <dd>{{ auth.state.user?.roles.includes('ADMIN') ? '平台管理员' : '项目成员' }}</dd>
        </div>
        <div>
          <dt>创建时间</dt>
          <dd>
            {{
              auth.state.user?.createdAt
                ? new Date(auth.state.user.createdAt).toLocaleString('zh-CN')
                : '—'
            }}
          </dd>
        </div>
      </dl>
      <p class="muted">会话仅保存在当前标签页，关闭标签页后需要重新登录。</p>
      <RouterLink class="button button-secondary" to="/projects">我的项目</RouterLink>
      <RouterLink class="button button-secondary" to="/account/password">修改密码</RouterLink>
      <button class="button button-secondary" @click="auth.logout('你已退出登录。')">
        退出登录
      </button>
    </section>
  </div>
</template>
