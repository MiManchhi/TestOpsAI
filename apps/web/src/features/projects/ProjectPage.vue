<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { projects } from './context';
import {
  createProject,
  getProject,
  listMembers,
  removeMember,
  setMember,
  updateProject,
} from './api';
import { idempotencyKey, useProjectAction, useResource } from '../../app/resource';
import type { Member, SetMember } from '../../app/contracts';
import RequestState from '../../app/RequestState.vue';
import PageControls from '../../app/PageControls.vue';
const project = projects.current;
const owner = computed(() => project.value?.role === 'OWNER');
const roles = { OWNER: '项目所有者', EDITOR: '编辑者', VIEWER: '查看者' };
const page = ref(0);
const details = useResource(
  () => projects.state.selectedId,
  (signal) => getProject(projects.state.selectedId, signal),
);
const members = useResource(
  () => `${page.value}:${owner.value}`,
  (signal) =>
    owner.value
      ? listMembers(projects.state.selectedId, page.value, signal)
      : Promise.resolve(null),
);
const edit = reactive({ name: '', description: '' });
const create = reactive({ key: '', name: '', description: '' });
const userId = ref('');
const role = ref<SetMember['role']>('VIEWER');
const action = useProjectAction();
const creation = useProjectAction();
const getKey = idempotencyKey();
const dialog = ref<HTMLDialogElement>();
const removal = ref<Member | null>(null);
watch(details.data, (value) => {
  if (value) {
    Object.assign(edit, { name: value.name, description: value.description });
    projects.updateCurrent(value);
  }
});
watch(
  () => projects.state.selectedId,
  () => {
    page.value = 0;
    userId.value = '';
    role.value = 'VIEWER';
    Object.assign(edit, { name: '', description: '' });
    dialog.value?.close();
    removal.value = null;
  },
  { flush: 'sync' },
);
function saveProject() {
  void action.run(
    (signal) => updateProject(projects.state.selectedId, { ...edit }, signal),
    (value) => {
      projects.updateCurrent(value);
      action.notice.value = '项目资料已保存。';
    },
  );
}
function saveMember() {
  void action.run(
    (signal) => setMember(projects.state.selectedId, userId.value, { role: role.value }, signal),
    async () => {
      userId.value = '';
      action.notice.value = '成员角色已保存。';
      await members.reload();
    },
  );
}
function askRemove(member: Member) {
  removal.value = member;
  dialog.value?.showModal();
}
function confirmRemove() {
  const target = removal.value;
  if (!target) return;
  void action.run(
    (signal) => removeMember(projects.state.selectedId, target.userId, signal),
    async () => {
      dialog.value?.close();
      removal.value = null;
      action.notice.value = '成员访问权限已撤销。';
      await members.reload();
    },
  );
}
function submitCreate() {
  const body = { ...create };
  void creation.run(
    (signal) => createProject(body, getKey(JSON.stringify(body)), signal),
    async (value) => {
      Object.assign(create, { key: '', name: '', description: '' });
      await projects.load(value.id);
      creation.notice.value = `项目“${value.name}”已创建。`;
    },
  );
}
</script>
<template>
  <div class="flow-page">
    <header class="page-heading-row">
      <div>
        <p class="section-kicker">PROJECT / 访问与协作</p>
        <h1 data-page-heading tabindex="-1">项目与成员</h1>
        <p class="muted">管理项目资料，明确每位成员的访问范围。</p>
      </div>
    </header>
    <section v-if="project" class="panel flow-panel">
      <div class="panel-heading">
        <h2>{{ project.name }}</h2>
        <span class="status-tag">{{ roles[project.role] }}</span>
      </div>
      <p class="muted">{{ project.key }} · #{{ project.id }}</p>
      <RequestState
        :loading="details.loading.value"
        :error="details.error.value"
        :trace="details.trace.value"
        @retry="details.reload"
      />
      <form v-if="details.data.value && owner" class="flow-form" @submit.prevent="saveProject">
        <fieldset :disabled="action.busy.value">
          <label>项目名称<input v-model="edit.name" maxlength="128" required /></label
          ><label>项目说明<textarea v-model="edit.description" maxlength="1000" rows="3" /></label
          ><button class="button button-primary" type="submit">保存项目资料</button>
        </fieldset>
      </form>
      <p v-else-if="details.data.value" class="preserve-text">
        {{ details.data.value.description || '暂无项目说明。' }}
      </p>
    </section>
    <section v-if="project && owner" class="panel flow-panel">
      <div class="panel-heading">
        <h2>项目成员</h2>
        <button class="text-button" :disabled="members.loading.value" @click="members.reload">
          刷新成员
        </button>
      </div>
      <p class="muted">编辑者可导入和维护内容；查看者可读取资料。所有者角色受保护。</p>
      <form class="flow-toolbar" @submit.prevent="saveMember">
        <label
          >用户 ID<input
            v-model="userId"
            inputmode="numeric"
            pattern="[1-9][0-9]{0,18}"
            maxlength="19"
            required
            :disabled="action.busy.value" /></label
        ><label
          >授予角色<select v-model="role" aria-label="授予角色" :disabled="action.busy.value">
            <option value="VIEWER">查看者</option>
            <option value="EDITOR">编辑者</option>
          </select></label
        ><button class="button button-primary" type="submit" :disabled="action.busy.value">
          保存成员角色
        </button>
      </form>
      <RequestState
        :loading="members.loading.value"
        :error="members.error.value"
        :trace="members.trace.value"
        @retry="members.reload"
      />
      <template v-if="members.data.value"
        ><p v-if="!members.data.value.items.length" class="flow-empty">暂无成员记录。</p>
        <div v-else class="flow-table-scroll">
          <table class="flow-table">
            <thead>
              <tr>
                <th>成员</th>
                <th>角色</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="member in members.data.value.items" :key="member.userId">
                <td>
                  <strong>{{ member.username }}</strong><small>#{{ member.userId }}</small>
                </td>
                <td>{{ roles[member.role] }}</td>
                <td>
                  <template v-if="member.role !== 'OWNER'"
                    ><button
                      class="text-button"
                      :disabled="action.busy.value"
                      @click="
                        userId = member.userId;
                        role = member.role;
                      "
                    >
                      修改角色</button
                    ><button
                      class="text-button danger-text"
                      :disabled="action.busy.value"
                      @click="askRemove(member)"
                    >
                      撤销访问
                    </button></template
                  ><span v-else class="muted">所有者受保护</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PageControls
          :page="page"
          :size="20"
          :total="members.data.value.total"
          @change="page = $event"
      /></template>
    </section>
    <p v-else-if="project" class="notice">当前角色可查看项目资料，成员管理需要所有者权限。</p>
    <p v-if="action.error.value" class="flow-error" role="alert">
      {{ action.error.value
      }}<small v-if="action.trace.value">请求编号：{{ action.trace.value }}</small>
    </p>
    <p v-if="action.notice.value" class="notice" role="status">{{ action.notice.value }}</p>
    <details class="panel flow-panel">
      <summary>创建新项目</summary>
      <form class="flow-form" @submit.prevent="submitCreate">
        <fieldset :disabled="creation.busy.value">
          <label
            >项目标识<input
              v-model="create.key"
              pattern="[A-Z](?:[A-Z0-9_]|-){1,63}"
              minlength="2"
              maxlength="64"
              required
              placeholder="例如 METER_DEMO" /></label
          ><label>项目名称<input v-model="create.name" maxlength="128" required /></label
          ><label>项目说明<textarea v-model="create.description" maxlength="1000" rows="3" /></label
          ><button class="button button-primary" type="submit">创建项目</button>
        </fieldset>
      </form>
      <p v-if="creation.error.value" class="flow-error" role="alert">{{ creation.error.value }}</p>
      <p v-if="creation.notice.value" class="notice" role="status">{{ creation.notice.value }}</p>
    </details>
    <dialog
      ref="dialog"
      class="flow-dialog"
      aria-labelledby="remove-member-title"
      @close="removal = null"
    >
      <h2 id="remove-member-title">撤销成员访问？</h2>
      <p>{{ removal?.username }} 将无法访问此项目，已有记录仍保留。</p>
      <p v-if="action.error.value" class="flow-error" role="alert">{{ action.error.value }}</p>
      <div class="flow-actions">
        <button
          class="button button-secondary"
          :disabled="action.busy.value"
          @click="dialog?.close()"
        >
          取消</button
        ><button class="button button-primary" :disabled="action.busy.value" @click="confirmRemove">
          确认撤销
        </button>
      </div>
    </dialog>
  </div>
</template>
