<script setup lang="ts">
import { computed, onScopeDispose, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { projects } from '../projects/context';
import type { DraftItem } from '../../app/contracts';
import { commitDraft, discardDraft, editDraft, getDraft } from './api';
import { idempotencyKey, useProjectAction, useResource } from '../../app/resource';
import RequestState from '../../app/RequestState.vue';
import CitationDialog from '../knowledge/CitationDialog.vue';
const route = useRoute(),
  id = computed(() => String(route.params.draftId ?? ''));
const detail = useResource(
  () => id.value,
  (signal) => getDraft(projects.state.selectedId, id.value, signal),
);
const items = ref<DraftItem[]>([]),
  selected = ref<string[]>([]),
  citationId = ref('');
const tags = ref<Record<string, string>>({});
const now = ref(Date.now()),
  dialog = ref<HTMLDialogElement>(),
  operation = ref<'commit' | 'discard'>('commit');
const timer = setInterval(() => {
  now.value = Date.now();
}, 30_000);
onScopeDispose(() => {
  clearInterval(timer);
  dialog.value?.close();
});
const action = useProjectAction(),
  key = idempotencyKey();
watch(detail.data, (value) => {
  if (!value) return;
  items.value = JSON.parse(JSON.stringify(value.items)) as DraftItem[];
  tags.value = Object.fromEntries(
    value.items.map((item) => [item.id, (item.values.tags ?? []).join(', ')]),
  );
  selected.value = value.items.map((item) => item.id);
});
watch(
  () => action.status.value,
  (status) => {
    if ([403, 404].includes(status)) {
      detail.data.value = null;
      items.value = [];
      selected.value = [];
      citationId.value = '';
      dialog.value?.close();
    }
  },
);
watch(
  () => projects.state.selectedId,
  () => {
    items.value = [];
    selected.value = [];
    citationId.value = '';
    dialog.value?.close();
  },
  { flush: 'sync' },
);
const expired = computed(
  () => !!detail.data.value && new Date(detail.data.value.expiresAt).getTime() <= now.value,
);
const editable = computed(
  () =>
    detail.data.value?.status === 'EDITABLE' &&
    !expired.value &&
    projects.current.value?.role !== 'VIEWER',
);
function payload() {
  return items.value.map((item) => ({
    ...item,
    values: {
      ...item.values,
      tags: [
        ...new Set(
          (tags.value[item.id] ?? '')
            .split(/[,，]/)
            .map((tag) => tag.trim())
            .filter(Boolean),
        ),
      ],
    },
  }));
}
const dirty = computed(
  () =>
    !!detail.data.value &&
    JSON.stringify(payload()) !==
      JSON.stringify(
        detail.data.value.items.map((item) => ({
          ...item,
          values: { ...item.values, tags: item.values.tags ?? [] },
        })),
      ),
);
function save() {
  const draft = detail.data.value;
  if (!draft || !editable.value) return;
  void action.run(
    (signal) =>
      editDraft(
        projects.state.selectedId,
        id.value,
        { revision: draft.revision, items: payload() },
        signal,
      ),
    (value) => {
      detail.data.value = value;
      action.notice.value = '草稿修改已保存，请重新检查选中范围后确认。';
    },
  );
}
function ask(op: 'commit' | 'discard') {
  operation.value = op;
  dialog.value?.showModal();
}
function confirm() {
  const draft = detail.data.value;
  now.value = Date.now();
  if (!draft || !editable.value) {
    action.error.value = '草稿已不可编辑，请重新读取状态。';
    return;
  }
  if (operation.value === 'discard') {
    void action.run(
      (signal) => discardDraft(projects.state.selectedId, id.value, signal),
      () => {
        detail.data.value = { ...draft, status: 'DISCARDED' };
        dialog.value?.close();
        action.notice.value = '草稿已丢弃。';
      },
    );
    return;
  }
  if (dirty.value || !selected.value.length) return;
  const body = {
    revision: draft.revision,
    payloadHash: draft.payloadHash,
    selectedItemIds: [...selected.value].sort(),
  };
  void action.run(
    (signal) =>
      commitDraft(
        projects.state.selectedId,
        id.value,
        body,
        key(JSON.stringify([projects.state.selectedId, id.value, body])),
        signal,
      ),
    (result) => {
      detail.data.value = { ...draft, status: 'COMMITTED', createdCaseIds: result.createdCaseIds };
      dialog.value?.close();
      action.notice.value = `草稿已确认，实际保存 ${result.createdCaseIds.length} 条人工用例。${result.createdCaseIds.length !== body.selectedItemIds.length ? '结果与当前选择数量不同，请核对原保存结果。' : ''}`;
    },
  );
}
</script>
<template>
  <div class="flow-page">
    <RouterLink
      class="text-button"
      :to="{
        path: detail.data.value ? '/assistant/' + detail.data.value.conversationId : '/assistant',
        query: { projectId: projects.state.selectedId },
      }"
      >← 返回 AI 会话</RouterLink
    >
    <header class="page-heading-row">
      <div>
        <p class="section-kicker">DRAFT / 人工检查与确认</p>
        <h1 data-page-heading tabindex="-1">用例建议草稿</h1>
        <p class="muted">核对建议与来源，明确选中的字段后再保存。</p>
      </div>
      <button
        class="button button-secondary"
        :disabled="action.busy.value || detail.loading.value"
        @click="detail.reload"
      >
        重新读取并检查
      </button>
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
      ><div class="notice">
        版本 {{ detail.data.value.revision }} ·
        {{
          { EDITABLE: '可编辑', COMMITTED: '已保存', DISCARDED: '已丢弃', EXPIRED: '已过期' }[
            detail.data.value.status
          ]
        }}
        · 有效期至
        {{
          new Date(detail.data.value.expiresAt).toLocaleString('zh-CN')
        }}。重新读取会丢弃未保存的本地修改。
      </div>
      <p v-if="expired || detail.data.value.status === 'EXPIRED'" class="flow-error">
        草稿已过期，请返回会话重新获取建议。
      </p>
      <p v-else-if="projects.current.value?.role === 'VIEWER'" class="notice">
        当前角色可检查建议，编辑和保存需要编辑者或所有者权限。
      </p>
      <div v-if="detail.data.value.status === 'EDITABLE'" class="draft-items">
        <article v-for="(item, index) in items" :key="item.id" class="panel flow-panel">
          <div class="panel-heading">
            <h2>建议 {{ index + 1 }}</h2>
            <label class="flow-check"
              ><input
                v-model="selected"
                type="checkbox"
                :value="item.id"
                :disabled="!editable || action.busy.value"
              />保存此条</label
            >
          </div>
          <fieldset class="draft-fields" :disabled="!editable || action.busy.value">
            <div class="form-grid">
              <label>用例名称<input v-model="item.values.title" required maxlength="256" /></label
              ><label>所属模块<input v-model="item.values.module" required maxlength="128" /></label
              ><label
                >优先级<select v-model="item.values.priority" aria-label="优先级">
                  <option v-for="priority in ['P0', 'P1', 'P2', 'P3']" :key="priority">
                    {{ priority }}
                  </option>
                </select></label
              ><label>标签（逗号分隔）<input v-model="tags[item.id]" /></label>
            </div>
            <label
              >说明<textarea
                v-model="item.values.documentation"
                maxlength="10000"
                rows="3"
              /></label
            ><label
              >预期结果<textarea
                v-model="item.values.expectedResult"
                required
                maxlength="4000"
                rows="3"
              />
            </label>
          </fieldset>
          <div v-if="item.citationIds.length" class="flow-actions">
            <button
              v-for="(source, sourceIndex) in item.citationIds"
              :key="source"
              class="citation-button"
              @click="citationId = source"
            >
              [{{ sourceIndex + 1 }}] 检查依据
            </button>
          </div>
        </article>
      </div>
      <section v-if="editable" class="panel flow-panel">
        <p v-if="dirty" class="notice">存在未保存修改，请先保存草稿，再检查选中范围并确认。</p>
        <div class="flow-actions">
          <button
            class="button button-secondary"
            :disabled="action.busy.value || !dirty"
            @click="save"
          >
            保存草稿修改</button
          ><button
            class="button button-primary"
            :disabled="action.busy.value || dirty || !selected.length"
            @click="ask('commit')"
          >
            确认保存选中 {{ selected.length }} 条用例</button
          ><button
            class="text-button danger-text"
            :disabled="action.busy.value"
            @click="ask('discard')"
          >
            丢弃草稿
          </button>
        </div>
      </section>
      <section v-if="detail.data.value.status === 'COMMITTED'" class="panel flow-panel">
        <h2>已保存的人工用例</h2>
        <div class="flow-actions">
          <RouterLink
            v-for="caseId in detail.data.value.createdCaseIds"
            :key="caseId"
            class="text-button"
            :to="{ path: '/test-cases/' + caseId, query: { projectId: projects.state.selectedId } }"
            >查看用例 #{{ caseId }} →</RouterLink
          >
        </div>
      </section></template
    >
    <dialog ref="dialog" class="flow-dialog" aria-labelledby="draft-confirm-title">
      <h2 id="draft-confirm-title">
        {{ operation === 'commit' ? '确认保存人工用例？' : '丢弃此草稿？' }}
      </h2>
      <template v-if="operation === 'commit'"
        ><p>将保存选中的 {{ selected.length }} 条人工用例，不会执行 Robot 或生成设备运行。</p>
        <ul>
          <li v-for="item in items.filter((value) => selected.includes(value.id))" :key="item.id">
            {{ item.values.title }}
          </li>
        </ul></template
      >
      <p v-else>此草稿将无法继续编辑，已保存的业务记录不会删除。</p>
      <p v-if="action.error.value" class="flow-error" role="alert">{{ action.error.value }}</p>
      <div class="flow-actions">
        <button
          class="button button-secondary"
          :disabled="action.busy.value"
          @click="dialog?.close()"
        >
          返回检查</button
        ><button
          class="button button-primary"
          :disabled="action.busy.value || !editable"
          @click="confirm"
        >
          {{ operation === 'commit' ? '确认保存' : '确认丢弃' }}
        </button>
      </div>
    </dialog>
    <CitationDialog :citation-id="citationId" @close="citationId = ''" />
  </div>
</template>
