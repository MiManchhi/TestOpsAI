<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ApiError, errorMessage } from '../../app/http';
import type {
  WorkspaceHandover,
  WorkspaceMember,
  WorkspaceProject,
  WorkspaceUser,
} from '../../app/workspace-contracts';
import {
  decideOwnerTransfer,
  editWorkspaceProject,
  listEligibleOwners,
  listMemberCandidates,
  listOwnerTransfers,
  listWorkspaceMembers,
  proxyOwnerTransfer,
  readWorkspaceProject,
  removeWorkspaceMember,
  requestOwnerTransfer,
  saveWorkspaceMember,
} from './workspace-api';

type MemberRole = 'TEST' | 'DEV';
const props = defineProps<{ platform: boolean }>();
const route = useRoute();
const project = ref<WorkspaceProject | null>(null);
const members = ref<WorkspaceMember[]>([]);
const history = ref<WorkspaceHandover[]>([]);
const candidates = ref<WorkspaceUser[]>([]);
const memberPage = ref(0);
const memberTotal = ref(0);
const historyPage = ref(0);
const historyTotal = ref(0);
const searchPage = ref(0);
const searchTotal = ref(0);
const q = ref('');
const lastQuery = ref('');
const selection = ref('');
const role = ref<MemberRole>('TEST');
const formerRole = ref<MemberRole>('TEST');
const successor = ref('');
const leaveReason = ref('');
const decisionNote = ref('');
const pending = computed(() => history.value.find((item) => item.status === 'PENDING'));
const name = ref('');
const description = ref('');
const busy = ref(false);
const loading = ref(true);
const error = ref('');
const notice = ref('');
const removeTarget = ref<WorkspaceMember>();
let controller: AbortController | undefined;
let searchController: AbortController | undefined;
let lastMutation: { fingerprint: string; key: string } | undefined;

watch(
  () => [route.params.projectId, props.platform] as const,
  async ([raw]) => {
    controller?.abort();
    searchController?.abort();
    const next = new AbortController();
    controller = next;
    lastMutation = undefined;
    removeTarget.value = undefined;
    busy.value = false;
    project.value = null;
    members.value = [];
    history.value = [];
    candidates.value = [];
    selection.value =
      successor.value =
      q.value =
      leaveReason.value =
      decisionNote.value =
      notice.value =
      error.value =
        '';
    loading.value = true;
    try {
      if (typeof raw !== 'string') return;
      const loaded = await readWorkspaceProject(raw, props.platform, next.signal);
      if (next.signal.aborted) return;
      project.value = loaded;
      name.value = loaded.name;
      description.value = loaded.description;
      if (!props.platform && loaded.myRole !== 'OWNER') return;
      await refresh(0, 0);
    } catch (failure) {
      if (!next.signal.aborted) error.value = errorMessage(failure);
    } finally {
      if (!next.signal.aborted) loading.value = false;
    }
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  controller?.abort();
  searchController?.abort();
});

async function refresh(step = memberPage.value, transferPage = historyPage.value) {
  const value = project.value;
  const signal = controller?.signal;
  if (!value || !signal) return;
  const [handover, member] = await Promise.all([
    listOwnerTransfers(value.projectId, props.platform, transferPage, signal),
    props.platform ? Promise.resolve(null) : listWorkspaceMembers(value.projectId, step, signal),
  ]);
  if (signal.aborted) return;
  history.value = handover.items;
  historyPage.value = handover.page;
  historyTotal.value = handover.total;
  if (member) {
    members.value = member.items;
    memberPage.value = member.page;
    memberTotal.value = member.total;
  }
}

async function reloadList(member: number, transfer: number) {
  try {
    await refresh(member, transfer);
  } catch (failure) {
    if (!controller?.signal.aborted) error.value = errorMessage(failure);
  }
}

async function searchPeople(page = 0, query = q.value) {
  if (!project.value) return;
  searchController?.abort();
  const search = new AbortController();
  searchController = search;
  try {
    const id = project.value.projectId;
    const result = await (props.platform ? listEligibleOwners : listMemberCandidates)(
      id,
      page,
      query,
      search.signal,
    );
    if (search.signal.aborted) return;
    candidates.value = result.items;
    searchPage.value = result.page;
    searchTotal.value = result.total;
    lastQuery.value = query;
  } catch (failure) {
    if (!search.signal.aborted) error.value = errorMessage(failure);
  }
}

async function mutate(
  data: unknown,
  action: (id: string, key: string, signal: AbortSignal) => Promise<unknown>,
) {
  const id = project.value?.projectId;
  const signal = controller?.signal;
  if (!id || !signal || busy.value) return;
  const fingerprint = JSON.stringify([id, data]);
  if (lastMutation?.fingerprint !== fingerprint)
    lastMutation = { fingerprint, key: crypto.randomUUID() };
  error.value = notice.value = '';
  busy.value = true;
  try {
    await action(id, lastMutation.key, signal);
    if (signal.aborted) return;
    lastMutation = undefined;
    selection.value = successor.value = leaveReason.value = decisionNote.value = '';
    removeTarget.value = undefined;
    notice.value = '已保存。';
    project.value = await readWorkspaceProject(id, props.platform, signal);
    if (props.platform || project.value.myRole === 'OWNER') await refresh(0, 0);
  } catch (failure) {
    if (signal.aborted) return;
    error.value = errorMessage(failure);
    if (failure instanceof ApiError && failure.status === 409) {
      try {
        project.value = await readWorkspaceProject(id, props.platform, signal);
        await refresh(0, 0);
      } catch {
        // Keep the first error and the unsaved inputs visible.
      }
    }
  } finally {
    if (!signal.aborted) busy.value = false;
  }
}

function saveProject() {
  if (!project.value) return;
  const body = {
    name: name.value.trim(),
    description: description.value,
    revision: project.value.revision,
  };
  void mutate(['edit', body], (id, key, signal) => editWorkspaceProject(id, body, key, signal));
}

function saveMember(userId: string, nextRole: MemberRole, revision = 0) {
  const body = { role: nextRole, revision };
  if (!userId) return;
  void mutate(['member', userId, body], (id, key, signal) =>
    saveWorkspaceMember(id, userId, body, key, signal),
  );
}

function removeMember() {
  const item = removeTarget.value;
  if (!item) return;
  void mutate(['remove', item.userId, item.revision], (id, key, signal) =>
    removeWorkspaceMember(id, item.userId, item.revision, key, signal),
  );
}

function transfer() {
  if (!project.value || !successor.value) return;
  const body = {
    newOwnerId: successor.value,
    formerRole: formerRole.value,
    revision: project.value.revision,
  };
  const reason = leaveReason.value.trim();
  void mutate(['transfer', body, reason], (id, key, signal) =>
    props.platform
      ? proxyOwnerTransfer(id, { ...body, unavailableReason: reason }, key, signal)
      : requestOwnerTransfer(id, body, key, signal),
  );
}

function decide(decision: 'APPROVE' | 'RETURN') {
  const item = pending.value;
  if (!item) return;
  const body = { decision, note: decisionNote.value.trim(), revision: item.revision };
  if (decision === 'RETURN' && !body.note) {
    error.value = '退回交接时请填写原因。';
    return;
  }
  void mutate([item.handoverId, body], (id, key, signal) =>
    decideOwnerTransfer(id, item.handoverId, body, key, signal),
  );
}

const labels = { OWNER: '负责人', TEST: '测试工程师', DEV: '开发工程师' } as const;
const statuses = { PENDING: '待确认', APPROVED: '已批准', RETURNED: '已退回' } as const;
</script>

<template>
  <section :aria-busy="loading">
    <RouterLink :to="platform ? '/platform/projects' : '/projects'">返回项目列表</RouterLink>
    <h1 data-page-heading tabindex="-1">{{ project?.name ?? '项目管理' }}</h1>
    <p v-if="loading" role="status">正在读取项目信息…</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <template v-if="project">
      <section class="detail-panel settings-panel">
        <h2>基本信息</h2>
        <p>负责人 {{ project.owner.displayName }}</p>
        <p v-if="platform">{{ project.description }}</p>
        <form v-else-if="project.myRole === 'OWNER'" class="fields" @submit.prevent="saveProject">
          <label>项目名称 <input v-model="name" maxlength="160" required :disabled="busy" /></label>
          <label>说明 <textarea v-model="description" rows="2" :disabled="busy" /></label>
          <button class="button button-primary" :disabled="busy">保存项目</button>
        </form>
      </section>

      <section v-if="!platform && project.myRole === 'OWNER'" class="detail-panel settings-panel">
        <h2>项目成员</h2>
        <ul class="members">
          <li v-for="item in members" :key="item.userId">
            <span>{{ item.displayName }} · {{ labels[item.role] }}</span>
            <div v-if="item.role !== 'OWNER'" class="button-row">
              <button
                class="button button-secondary"
                :disabled="busy"
                @click="
                  saveMember(item.userId, item.role === 'TEST' ? 'DEV' : 'TEST', item.revision)
                "
              >
                设为{{ item.role === 'TEST' ? '开发' : '测试' }}
              </button>
              <button class="button button-secondary" :disabled="busy" @click="removeTarget = item">
                移出
              </button>
            </div>
          </li>
        </ul>
        <div v-if="removeTarget" class="button-row">
          <p>确认移出 {{ removeTarget.displayName }}？该成员将失去项目访问权限。</p>
          <button class="button button-primary" :disabled="busy" @click="removeMember">
            确认移出
          </button>
          <button
            class="button button-secondary"
            :disabled="busy"
            @click="removeTarget = undefined"
          >
            取消
          </button>
        </div>
        <div class="button-row">
          <button
            class="button button-secondary"
            :disabled="memberPage === 0 || busy"
            @click="reloadList(memberPage - 1, historyPage)"
          >
            上一页
          </button>
          <button
            class="button button-secondary"
            :disabled="(memberPage + 1) * 20 >= memberTotal || busy"
            @click="reloadList(memberPage + 1, historyPage)"
          >
            下一页
          </button>
        </div>
      </section>

      <section v-if="platform || project.myRole === 'OWNER'" class="detail-panel settings-panel">
        <h2>{{ platform ? '交接审核与代办' : '加入成员与负责人交接' }}</h2>

        <template v-if="platform && pending">
          <p>待确认申请 · {{ pending.oldOwnerId }} → {{ pending.newOwnerId }}</p>
          <label>审核说明 <input v-model="decisionNote" :disabled="busy" /></label>
          <div class="button-row">
            <button class="button button-primary" :disabled="busy" @click="decide('APPROVE')">
              批准交接
            </button>
            <button class="button button-secondary" :disabled="busy" @click="decide('RETURN')">
              退回申请
            </button>
          </div>
        </template>
        <form class="button-row" @submit.prevent="searchPeople()">
          <label
            >{{ platform ? '查找可接任成员' : '查找可加入的账号' }}
            <input v-model="q" type="search" :disabled="busy"
          /></label>
          <button class="button button-secondary" :disabled="busy">搜索</button>
        </form>
        <label
          >候选人
          <select v-model="selection" :disabled="busy">
            <option value="">请选择</option>
            <option v-for="item in candidates" :key="item.userId" :value="item.userId">
              {{ item.displayName }} · {{ item.userId }}
            </option>
          </select>
        </label>
        <div class="button-row">
          <button
            class="button button-secondary"
            :disabled="searchPage === 0 || busy"
            @click="searchPeople(searchPage - 1, lastQuery)"
          >
            上页
          </button>
          <button
            class="button button-secondary"
            :disabled="(searchPage + 1) * 20 >= searchTotal || busy"
            @click="searchPeople(searchPage + 1, lastQuery)"
          >
            下页
          </button>
        </div>
        <form v-if="!platform" class="button-row" @submit.prevent="saveMember(selection, role)">
          <label
            >成员角色
            <select v-model="role" :disabled="busy">
              <option value="TEST">测试</option>
              <option value="DEV">开发</option>
            </select></label
          >
          <button class="button button-primary" :disabled="busy || !selection">添加成员</button>
        </form>
        <form class="fields" @submit.prevent="transfer">
          <label
            >接任人
            <select v-model="successor" :disabled="busy">
              <option value="">请选择</option>
              <option
                v-for="item in platform ? candidates : members.filter((m) => m.role !== 'OWNER')"
                :key="item.userId"
                :value="item.userId"
              >
                {{ item.displayName }}
              </option>
            </select>
          </label>
          <label
            >原负责人的新角色
            <select v-model="formerRole" :disabled="busy">
              <option value="TEST">测试</option>
              <option value="DEV">开发</option>
            </select></label
          >
          <label v-if="platform"
            >原负责人离岗说明 <input v-model="leaveReason" required :disabled="busy"
          /></label>
          <button
            class="button button-primary"
            :disabled="busy || !successor || (platform && !!pending)"
          >
            {{ platform ? '确认代办交接' : '提交交接申请' }}
          </button>
        </form>
        <h3>交接记录</h3>
        <p v-if="!history.length" class="muted">暂无交接记录</p>
        <ul v-else>
          <li v-for="item in history" :key="item.handoverId">
            {{ item.oldOwnerId }} → {{ item.newOwnerId }} · {{ statuses[item.status]
            }}{{ item.reason ? ' · ' + item.reason : '' }}
          </li>
        </ul>
        <div class="button-row">
          <button
            class="button button-secondary"
            :disabled="historyPage === 0 || busy"
            @click="reloadList(memberPage, historyPage - 1)"
          >
            上页记录
          </button>
          <button
            class="button button-secondary"
            :disabled="(historyPage + 1) * 20 >= historyTotal || busy"
            @click="reloadList(memberPage, historyPage + 1)"
          >
            下页记录
          </button>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.settings-panel {
  margin-block: 0.9rem;
  display: grid;
  gap: 0.65rem;
}

.fields {
  display: grid;
  gap: 0.55rem;
}
.fields label {
  display: grid;
  gap: 0.3rem;
}
.members {
  list-style: none;
  padding: 0;
}
.members li,
.button-row {
  display: flex;
  gap: 0.6rem;
  align-items: center;
  flex-wrap: wrap;
}
.members li {
  justify-content: space-between;
  padding-block: 0.35rem;
}
button,
input,
select {
  min-height: 44px;
}
</style>
