<script setup lang="ts">
import { computed, onScopeDispose, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { projects } from '../projects/context';
import {
  categories,
  changeDocument,
  documentStage,
  documentStatus,
  getKnowledgeBase,
  listDocuments,
  listGrants,
  removeGrant,
  setGrant,
  updateKnowledgeBase,
  uploadDocument,
} from './api';
import type { Document, KnowledgeBasePatch, SetKnowledgeGrant } from '../../app/contracts';
import { idempotencyKey, useProjectAction, useResource } from '../../app/resource';
import RequestState from '../../app/RequestState.vue';
import PageControls from '../../app/PageControls.vue';
const route = useRoute(),
  kbId = computed(() => String(route.params.kbId ?? ''));
const query = computed(() => ({ projectId: projects.state.selectedId }));
const tab = ref<'documents' | 'settings' | 'grants'>('documents');
const owner = computed(() => projects.current.value?.role === 'OWNER');
const page = ref(0),
  grantPage = ref(0);
const detail = useResource(
  () => kbId.value,
  (signal) => getKnowledgeBase(projects.state.selectedId, kbId.value, signal),
);
const documents = useResource(
  () => `${kbId.value}:${page.value}`,
  (signal) => listDocuments(projects.state.selectedId, kbId.value, page.value, signal),
);
const grants = useResource(
  () => `${kbId.value}:${grantPage.value}:${tab.value}:${owner.value}`,
  (signal) =>
    owner.value && tab.value === 'grants'
      ? listGrants(projects.state.selectedId, kbId.value, grantPage.value, signal)
      : Promise.resolve(null),
);
const canWrite = computed(
  () => detail.data.value?.canWrite === true && projects.current.value?.role !== 'VIEWER',
);
const form = reactive<KnowledgeBasePatch>({
  name: '',
  description: '',
  category: 'METERING',
  visibility: 'PROJECT',
});
watch(detail.data, (value) => {
  if (value)
    Object.assign(form, {
      name: value.name,
      description: value.description,
      category: value.category,
      visibility: value.visibility,
    });
});
const action = useProjectAction(),
  getKey = idempotencyKey();
const file = ref<File>(),
  title = ref(''),
  versionLabel = ref('');
const fileGeneration = ref(0),
  inputKey = ref(0),
  documentIntent = ref(0);
const userId = ref(''),
  access = ref<SetKnowledgeGrant['access']>('READ');
const dialog = ref<HTMLDialogElement>();
const confirmation = ref<{ document: Document } | { userId: string } | null>(null);
let timer: ReturnType<typeof setTimeout> | undefined;
let polls = 0;
watch([documents.data, documents.loading], () => {
  clearTimeout(timer);
  if (
    !documents.loading.value &&
    documents.data.value?.items.some((item) =>
      ['QUEUED', 'PROCESSING', 'DELETING'].includes(item.status),
    ) &&
    polls++ < 150
  )
    timer = setTimeout(() => void documents.refresh(), 2000);
});
onScopeDispose(() => clearTimeout(timer));
watch(
  () => action.status.value,
  (status) => {
    if (status === 403 || status === 404) {
      detail.data.value = null;
      documents.data.value = null;
      grants.data.value = null;
      dialog.value?.close();
    }
  },
);
watch(
  () => projects.state.selectedId,
  () => {
    dialog.value?.close();
    file.value = undefined;
    title.value = versionLabel.value = userId.value = '';
    inputKey.value++;
    confirmation.value = null;
  },
  { flush: 'sync' },
);
function chooseFile(event: Event) {
  if (event.target instanceof HTMLInputElement) {
    file.value = event.target.files?.[0];
    fileGeneration.value++;
  }
}
function upload() {
  const pid = projects.state.selectedId;
  void action.run(
    (signal) => {
      if (!file.value) throw new Error('请选择文档。');
      return uploadDocument(
        pid,
        kbId.value,
        file.value,
        title.value,
        versionLabel.value,
        getKey(
          JSON.stringify([pid, kbId.value, fileGeneration.value, title.value, versionLabel.value]),
        ),
        signal,
      );
    },
    async () => {
      action.notice.value = '文档已受理，处理阶段以下方状态为准。';
      file.value = undefined;
      title.value = versionLabel.value = '';
      fileGeneration.value++;
      inputKey.value++;
      polls = 0;
      await documents.reload();
      await detail.reload();
    },
  );
}
function save() {
  void action.run(
    (signal) => updateKnowledgeBase(projects.state.selectedId, kbId.value, { ...form }, signal),
    (value) => {
      detail.data.value = value;
      action.notice.value = '知识库设置已保存。';
    },
  );
}
function retry(document: Document) {
  void action.run(
    (signal) =>
      changeDocument(
        projects.state.selectedId,
        kbId.value,
        document.id,
        'retry',
        getKey(`retry:${document.id}:${documentIntent.value}`),
        signal,
      ),
    async () => {
      documentIntent.value++;
      polls = 0;
      action.notice.value = '已创建重试请求。';
      await documents.reload();
    },
  );
}
function askRemove(value: { document: Document } | { userId: string }) {
  confirmation.value = value;
  dialog.value?.showModal();
}
function confirmRemove() {
  const value = confirmation.value;
  if (!value) return;
  void action.run(
    async (signal) => {
      if ('document' in value)
        return changeDocument(
          projects.state.selectedId,
          kbId.value,
          value.document.id,
          'delete',
          getKey(`delete:${value.document.id}:${documentIntent.value}`),
          signal,
        );
      await removeGrant(projects.state.selectedId, kbId.value, value.userId, signal);
      return null;
    },
    async () => {
      documentIntent.value++;
      dialog.value?.close();
      confirmation.value = null;
      action.notice.value =
        'document' in value ? '文档删除已受理，清理状态以下方记录为准。' : '访问授权已撤销。';
      polls = 0;
      await documents.reload();
      if (owner.value && tab.value === 'grants') await grants.reload();
    },
  );
}
function saveGrant() {
  void action.run(
    (signal) =>
      setGrant(
        projects.state.selectedId,
        kbId.value,
        userId.value,
        { access: access.value },
        signal,
      ),
    async () => {
      userId.value = '';
      action.notice.value = '访问授权已保存。';
      await grants.reload();
    },
  );
}
</script>
<template>
  <div class="flow-page">
    <RouterLink class="text-button" :to="{ path: '/knowledge', query }">← 返回知识库</RouterLink>
    <header class="page-heading-row">
      <div>
        <p class="section-kicker">KNOWLEDGE / 文档与访问</p>
        <h1 data-page-heading tabindex="-1">{{ detail.data.value?.name ?? '知识库详情' }}</h1>
        <p class="muted">{{ detail.data.value?.description }}</p>
      </div>
      <RouterLink
        v-if="detail.data.value?.readyCount"
        class="button button-primary"
        :to="{ path: '/assistant', query: { ...query, kbId } }"
        >问此知识库 →</RouterLink
      >
    </header>
    <RequestState
      :loading="detail.loading.value"
      :error="detail.error.value"
      :trace="detail.trace.value"
      @retry="detail.reload"
    />
    <p v-if="action.error.value" class="flow-error" role="alert">
      {{ action.error.value
      }}<small v-if="action.trace.value">请求编号：{{ action.trace.value }}</small>
    </p>
    <p v-if="action.notice.value" class="notice" role="status">{{ action.notice.value }}</p>
    <template v-if="detail.data.value"
      ><div class="flow-tabs" aria-label="知识库内容">
        <button
          v-for="item in ['documents', 'settings', 'grants'] as const"
          :key="item"
          class="button"
          :class="tab === item ? 'button-primary' : 'button-secondary'"
          :aria-pressed="tab === item"
          @click="tab = item"
        >
          {{ { documents: '文档', settings: '设置', grants: '访问权限' }[item] }}
        </button>
      </div>
      <template v-if="tab === 'documents'">
        <section v-if="canWrite" class="panel flow-panel">
          <h2>上传新版本文档</h2>
          <p class="muted">
            Markdown 最大 2 MiB，PDF 最大 20 MiB。每次上传保留独立版本，不覆盖原文。
          </p>
          <form class="flow-form" @submit.prevent="upload">
            <fieldset :disabled="action.busy.value">
              <label class="file-drop"
                >选择 Markdown 或 PDF<input
                  :key="inputKey"
                  type="file"
                  accept=".md,.pdf"
                  required
                  @change="chooseFile"
              /></label>
              <div class="form-grid">
                <label>文档标题<input v-model="title" maxlength="256" required /></label
                ><label
                  >版本标签<input
                    v-model="versionLabel"
                    maxlength="64"
                    required
                    placeholder="例如 规约 1.0"
                /></label>
              </div>
              <button class="button button-primary" type="submit">上传文档</button>
            </fieldset>
          </form>
        </section>
        <section class="panel flow-panel">
          <div class="panel-heading">
            <h2>文档</h2>
            <button
              class="text-button"
              :disabled="documents.loading.value"
              @click="
                polls = 0;
                documents.reload();
              "
            >
              刷新状态
            </button>
          </div>
          <RequestState
            :loading="documents.loading.value && !documents.data.value"
            :error="documents.error.value"
            :trace="documents.trace.value"
            @retry="documents.reload"
          />
          <template v-if="documents.data.value"
            ><p v-if="!documents.data.value.items.length" class="flow-empty">
              知识库中还没有文档。上传后可在此查看处理状态。
            </p>
            <ul v-else class="document-list">
              <li v-for="document in documents.data.value.items" :key="document.id">
                <div class="panel-heading">
                  <div>
                    <RouterLink
                      v-if="document.status === 'READY'"
                      class="flow-title-link"
                      :to="{ path: `/knowledge/${kbId}/documents/${document.id}`, query }"
                      >{{ document.title }}</RouterLink><strong v-else>{{ document.title }}</strong
                    ><small>{{ document.versionLabel }} · {{ document.mimeType }}</small>
                  </div>
                  <span class="status-tag" :data-status="document.status">{{
                    documentStatus[document.status]
                  }}</span>
                </div>
                <p class="muted">{{ documentStage[document.stage] }}</p>
                <p v-if="document.error" class="flow-error">
                  {{ document.error.message
                  }}<small>请求编号：{{ document.error.requestId }}</small>
                </p>
                <div class="flow-actions">
                  <RouterLink
                    v-if="document.status === 'READY'"
                    class="text-button"
                    :to="{ path: '/assistant', query: { ...query, kbId, documentId: document.id } }"
                    >问这篇文档 →</RouterLink
                  ><button
                    v-if="canWrite && document.status === 'FAILED'"
                    class="text-button"
                    :disabled="action.busy.value"
                    @click="retry(document)"
                  >
                    重试处理</button
                  ><button
                    v-if="canWrite && !['DELETING', 'DELETED'].includes(document.status)"
                    class="text-button danger-text"
                    :disabled="action.busy.value"
                    @click="askRemove({ document })"
                  >
                    删除文档
                  </button>
                </div>
              </li>
            </ul>
            <PageControls
              :page="page"
              :size="20"
              :total="documents.data.value.total"
              @change="page = $event"
            />
            <p v-if="polls >= 150" class="muted">自动刷新已暂停，可手动刷新状态。</p></template
          >
        </section>
      </template>
      <section v-else-if="tab === 'settings'" class="panel flow-panel">
        <h2>知识库设置</h2>
        <form v-if="canWrite" class="flow-form" @submit.prevent="save">
          <fieldset :disabled="action.busy.value">
            <label>知识库名称<input v-model="form.name" maxlength="128" required /></label
            ><label
              >用途说明<textarea v-model="form.description" maxlength="1000" rows="3" />
            </label>
            <div class="form-grid">
              <label
                >资料领域<select v-model="form.category" aria-label="资料领域">
                  <option v-for="(label, value) in categories" :key="value" :value="value">
                    {{ label }}
                  </option>
                </select></label
              ><label
                >访问范围<select v-model="form.visibility" aria-label="访问范围">
                  <option value="PROJECT">项目成员可读</option>
                  <option value="RESTRICTED">仅获授权成员可读</option>
                </select></label
              >
            </div>
            <p v-if="form.visibility === 'PROJECT'" class="notice">
              保存后，项目内所有成员均可读取此知识库。
            </p>
            <button class="button button-primary" type="submit">保存知识库设置</button>
          </fieldset>
        </form>
        <p v-else class="notice">你可以阅读此知识库，修改设置需要写入权限。</p>
      </section>
      <section v-else class="panel flow-panel">
        <h2>访问权限</h2>
        <p v-if="detail.data.value.visibility === 'PROJECT'" class="notice">
          项目成员均可读取；编辑者和所有者可写入。
        </p>
        <template v-else-if="owner"
          ><p class="muted">
            仅限现有项目成员。WRITE 同时要求项目编辑者角色，READ 不授予项目外访问。
          </p>
          <form class="flow-toolbar" @submit.prevent="saveGrant">
            <label
              >成员用户 ID<input
                v-model="userId"
                pattern="[1-9][0-9]{0,18}"
                inputmode="numeric"
                required
                :disabled="action.busy.value" /></label
            ><label
              >授权级别<select v-model="access" aria-label="授权级别" :disabled="action.busy.value">
                <option value="READ">READ · 阅读</option>
                <option value="WRITE">WRITE · 编辑</option>
              </select></label
            ><button class="button button-primary" type="submit" :disabled="action.busy.value">
              保存授权
            </button>
          </form>
          <RequestState
            :loading="grants.loading.value"
            :error="grants.error.value"
            :trace="grants.trace.value"
            @retry="grants.reload" /><template v-if="grants.data.value"
            ><ul class="artifact-list">
              <li v-for="grant in grants.data.value.items" :key="grant.userId">
                <span>#{{ grant.userId }} · {{ grant.access }}</span
                ><button
                  class="text-button danger-text"
                  :disabled="action.busy.value"
                  @click="askRemove({ userId: grant.userId })"
                >
                  撤销授权
                </button>
              </li>
            </ul>
            <p v-if="!grants.data.value.items.length" class="flow-empty">暂无额外授权。</p>
            <PageControls
              :page="grantPage"
              :size="20"
              :total="grants.data.value.total"
              @change="grantPage = $event" /></template
        ></template>
        <p v-else class="notice">限制库访问由项目所有者管理。</p>
      </section>
    </template>
    <dialog
      ref="dialog"
      class="flow-dialog"
      aria-labelledby="knowledge-remove-title"
      @close="confirmation = null"
    >
      <h2 id="knowledge-remove-title">
        {{ confirmation && 'document' in confirmation ? '删除文档？' : '撤销访问授权？' }}
      </h2>
      <p>
        {{
          confirmation && 'document' in confirmation
            ? `“${confirmation.document.title}”将进入清理流程，旧引用可能无法再读取。`
            : '该成员将失去此限制库的额外访问权限。'
        }}
      </p>
      <p v-if="action.error.value" class="flow-error" role="alert">{{ action.error.value }}</p>
      <div class="flow-actions">
        <button
          class="button button-secondary"
          :disabled="action.busy.value"
          @click="dialog?.close()"
        >
          取消</button
        ><button class="button button-primary" :disabled="action.busy.value" @click="confirmRemove">
          确认操作
        </button>
      </div>
    </dialog>
  </div>
</template>
