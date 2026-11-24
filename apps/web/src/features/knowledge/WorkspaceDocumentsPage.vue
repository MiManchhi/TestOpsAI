<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { handoffId, returnToChat } from '../workspace/chat-handoff';
import { errorMessage } from '../../app/http';
import type { ReviewStatus, WorkspaceDocument } from '../../app/workspace-contracts';
import AppIcon from '../../app/AppIcon.vue';
import WorkspaceDocumentForm from './WorkspaceDocumentForm.vue';
import { listDocuments } from './workspace-api';
import { readWorkspaceProject } from '../projects/workspace-api';

const route = useRoute();
const router = useRouter();
const projectId = computed(() => String(route.params.projectId));
const rows = ref<WorkspaceDocument[]>([]);
const q = ref('');
const selected = ref<ReviewStatus | ''>('');
const page = ref(0);
const total = ref(0);
const busy = ref(false);
const error = ref('');
const creating = ref(false);
const owner = ref(false);
let query = '';
let status: ReviewStatus | undefined;
let controller: AbortController | undefined;
const reviews = { PENDING: '待审核', APPROVED: '已批准', RETURNED: '已退回' };
const indexes = {
  NOT_STARTED: '尚不可引用',
  QUEUED: '等待解析',
  PROCESSING: '解析中',
  READY: 'AI 可引用',
  FAILED: '解析失败',
};

watch(
  projectId,
  () => {
    rows.value = [];
    creating.value = route.path.endsWith('/documents/new');
    owner.value = false;
    query = q.value = '';
    selected.value = '';
    status = undefined;
    void load(0);
  },
  { immediate: true },
);
onBeforeUnmount(() => controller?.abort());

async function load(next: number) {
  controller?.abort();
  const request = new AbortController();
  controller = request;
  busy.value = true;
  error.value = '';
  try {
    const [result, project] = await Promise.all([
      listDocuments(projectId.value, next, request.signal, query, status),
      readWorkspaceProject(projectId.value, false, request.signal),
    ]);
    if (request.signal.aborted) return;
    owner.value = project.myRole === 'OWNER';
    rows.value = result.items;
    page.value = result.page;
    total.value = result.total;
  } catch (failure) {
    if (!request.signal.aborted) {
      rows.value = [];
      error.value = errorMessage(failure);
    }
  } finally {
    if (!request.signal.aborted) busy.value = false;
  }
}

function search() {
  query = q.value;
  status = selected.value || undefined;
  void load(0);
}
const detail = (item: WorkspaceDocument) =>
  `/p/${projectId.value}/documents/${item.documentId}/versions/${item.versionId}`;
function cancelForm() {
  if (route.path.endsWith('/documents/new'))
    void router.push('/p/' + projectId.value + '/documents');
  else creating.value = false;
}
async function saved(item: WorkspaceDocument) {
  creating.value = false;
  const id = handoffId(route.query.returnConversationId);
  await router.push({ path: detail(item), query: id ? { returnConversationId: id } : {} });
}
</script>

<template>
  <section>
    <RouterLink
      v-if="returnToChat(projectId, route.query)"
      :to="returnToChat(projectId, route.query)"
      >回到原对话</RouterLink
    >
    <div class="page-heading-row">
      <h1 data-page-heading tabindex="-1">{{ creating ? '提交资料' : '项目资料' }}</h1>
      <div class="button-row">
        <RouterLink
          v-if="owner && !creating"
          class="button button-secondary"
          :to="`/p/${projectId}/settings/bases`"
          >验收依据</RouterLink
        >
        <RouterLink
          v-if="!creating"
          class="button button-primary"
          :to="'/p/' + projectId + '/documents/new'"
          ><AppIcon name="plus" />提交资料</RouterLink
        >
      </div>
    </div>
    <WorkspaceDocumentForm
      v-if="creating"
      :key="projectId"
      :project-id="projectId"
      :attachment-id="handoffId(route.query.attachmentId)"
      @saved="saved"
      @cancel="cancelForm"
    />
    <template v-else
      ><form class="detail-panel button-row filter" @submit.prevent="search">
        <label>资料名称 <input v-model="q" type="search" /></label>
        <label
          >审核状态
          <select v-model="selected">
            <option value="">全部</option>
            <option value="PENDING">待审核</option>
            <option value="APPROVED">已批准</option>
            <option value="RETURNED">已退回</option>
          </select>
        </label>
        <button class="button button-secondary" :disabled="busy">筛选</button>
      </form>
      <p v-if="error" role="alert">{{ error }}</p>
      <section class="detail-panel" :aria-busy="busy">
        <p v-if="!rows.length">{{ busy ? '正在读取资料…' : '暂无资料' }}</p>
        <div v-else class="list-scroll">
          <table>
            <thead>
              <tr>
                <th>资料名称</th>
                <th>版本</th>
                <th>审核与检索</th>
                <th>读者范围</th>
                <th>提交人</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in rows" :key="item.versionId">
                <td>
                  <RouterLink class="doc-name" :to="detail(item)"
                    ><AppIcon name="book" />{{ item.title }}</RouterLink
                  >
                </td>
                <td>{{ item.versionLabel }}</td>
                <td>
                  <span class="status-tag" :data-state="item.reviewStatus">{{
                    reviews[item.reviewStatus]
                  }}</span><small>{{ indexes[item.indexStatus] }}</small>
                </td>
                <td>{{ item.visibility === 'PROJECT' ? '项目成员' : '限定读者' }}</td>
                <td>{{ item.submittedBy.displayName }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="total > 20" class="button-row">
          <button
            class="button button-secondary"
            :disabled="busy || page === 0"
            @click="load(page - 1)"
          >
            上一页
          </button>
          <button
            class="button button-secondary"
            :disabled="busy || (page + 1) * 20 >= total"
            @click="load(page + 1)"
          >
            下一页
          </button>
        </div>
      </section></template
    >
  </section>
</template>

<style scoped>
.button-row {
  display: flex;
  gap: 0.6rem;
  align-items: center;
  flex-wrap: wrap;
}
.filter {
  margin-block: 1rem;
}
.list-scroll {
  overflow: auto;
}
.list-scroll table {
  min-width: 590px;
}
.list-scroll small {
  display: block;
  margin-top: 4px;
  color: var(--muted);
}
.doc-name {
  display: flex;
  align-items: center;
  gap: 9px;
  color: var(--primary);
  font-weight: 600;
}
.doc-name .icon {
  height: 16px;
  width: 16px;
}
.status-tag {
  font-size: 12px;
  padding: 2px 7px;
  border-radius: 4px;
  background: #eef1f5;
}
.status-tag[data-state='APPROVED'] {
  background: #e3f3eb;
  color: #276249;
}
.status-tag[data-state='PENDING'] {
  color: #815e23;
  background: #fff3d9;
}
input,
select,
button {
  min-height: 44px;
}
</style>
