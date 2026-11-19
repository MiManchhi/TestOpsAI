<script setup lang="ts">
import { computed, onScopeDispose, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { auth } from '../auth/session';
import { projects } from '../projects/context';
import { getKnowledgeBase, getDocument, listDocuments, listKnowledgeBases } from '../knowledge/api';
import { getRun } from '../runs/api';
import { createConversation, deleteConversation, listConversations } from './api';
import { createConversationState } from './conversation';
import {
  readContract,
  type Document,
  type KnowledgeBase,
  type Message,
  type MessageContext,
  type Run,
  type TestContext,
} from '../../app/contracts';
import { idempotencyKey, useProjectAction, useResource } from '../../app/resource';
import RequestState from '../../app/RequestState.vue';
import PageControls from '../../app/PageControls.vue';
import AnswerCard from './AnswerCard.vue';
import SafeMarkdown from './SafeMarkdown.vue';
import CitationDialog from '../knowledge/CitationDialog.vue';
const route = useRoute(),
  router = useRouter();
const conversationId = computed(() =>
  typeof route.params.conversationId === 'string' ? route.params.conversationId : '',
);
const model = createConversationState();
const page = ref(0),
  search = ref(''),
  query = ref(''),
  newTitle = ref(''),
  question = ref(''),
  formError = ref(''),
  citationId = ref('');
const action = useProjectAction(),
  scopeAction = useProjectAction(),
  key = idempotencyKey();
const dialog = ref<HTMLDialogElement>(),
  removal = ref<{ id: string; title: string } | null>(null);
const conversations = useResource(
  () => `${page.value}:${query.value}`,
  (signal) => listConversations(projects.state.selectedId, page.value, query.value, signal),
);
const knowledgeMode = ref<'NONE' | 'SELECTED'>('NONE'),
  selectedKbs = ref<KnowledgeBase[]>([]),
  selectedDocs = ref<Document[]>([]);
const kbPage = ref(0),
  kbQuery = ref(''),
  kbSearch = ref(''),
  docPage = ref(0),
  docKbId = ref('');
const selectionId = ref(''),
  runInput = ref(''),
  selectedRun = ref<Run | null>(null);
const testContext = reactive<TestContext>({
  deviceType: 'UNSPECIFIED',
  productModel: '',
  firmwareVersion: '',
  hardwareVersion: '',
  protocolName: '',
  protocolVersion: '',
});
function resetContext() {
  knowledgeMode.value = 'NONE';
  selectedKbs.value = [];
  selectedDocs.value = [];
  selectionId.value = runInput.value = '';
  selectedRun.value = null;
  Object.assign(testContext, {
    deviceType: 'UNSPECIFIED',
    productModel: '',
    firmwareVersion: '',
    hardwareVersion: '',
    protocolName: '',
    protocolVersion: '',
  });
}
const sourceKey = computed(() =>
  JSON.stringify([
    route.query.kbId,
    route.query.documentId,
    route.query.runId,
    route.query.selectionId,
  ]),
);
watch(sourceKey, resetContext, { flush: 'sync' });
const source = useResource(
  () => sourceKey.value,
  async (signal) => {
    const value = (name: string) => {
      const raw = route.query[name];
      if (raw !== undefined && typeof raw !== 'string')
        throw new Error('附带的问题范围无效，请重新从业务页进入。');
      return typeof raw === 'string' ? raw : '';
    };
    const kbId = value('kbId'),
      documentId = value('documentId'),
      runId = value('runId'),
      selection = value('selectionId');
    if (documentId && !kbId) throw new Error('文档上下文需要所属知识库。');
    const pid = projects.state.selectedId;
    const kb = kbId ? await getKnowledgeBase(pid, kbId, signal) : null;
    const document = documentId ? await getDocument(pid, documentId, signal, kbId) : null;
    if ((kb && !kb.readyCount) || (document && document.status !== 'READY'))
      throw new Error('附带的知识资料尚未就绪，请检查资料或移除此范围。');
    const run = runId ? await getRun(pid, runId, signal) : null;
    if (selection) readContract('Uuid', selection);
    return { kb, document, run, selection };
  },
);
watch(source.data, (value) => {
  if (!value) return;
  selectedKbs.value = value.kb ? [value.kb] : [];
  selectedDocs.value = value.document ? [value.document] : [];
  knowledgeMode.value = value.kb ? 'SELECTED' : 'NONE';
  selectedRun.value = value.run;
  runInput.value = value.run?.id ?? '';
  if (value.run) Object.assign(testContext, value.run.testContext);
  selectionId.value = value.selection;
});
watch(
  [conversationId, () => projects.state.selectedId, () => auth.state.phase],
  () => {
    model.reset(auth.state.phase === 'authenticated' ? projects.state.selectedId : '');
    question.value = formError.value = citationId.value = '';
    dialog.value?.close();
    if (conversationId.value && auth.state.phase === 'authenticated')
      void model.load(conversationId.value);
  },
  { immediate: true, flush: 'sync' },
);
onScopeDispose(() => {
  model.reset('');
  dialog.value?.close();
});
watch(query, () => {
  page.value = 0;
});
watch(
  () => projects.state.selectedId,
  () => {
    resetContext();
    page.value = kbPage.value = docPage.value = 0;
    search.value = query.value = newTitle.value = '';
  },
  { flush: 'sync' },
);
watch(knowledgeMode, (mode) => {
  if (mode === 'NONE') {
    selectedKbs.value = [];
    selectedDocs.value = [];
  }
});
watch(selectedKbs, (values) => {
  if (!values.some((kb) => kb.id === docKbId.value)) {
    docKbId.value = values[0]?.id ?? '';
    docPage.value = 0;
  }
});
watch(docKbId, () => {
  docPage.value = 0;
});
const libraries = useResource(
  () => `${knowledgeMode.value}:${kbPage.value}:${kbQuery.value}`,
  (signal) =>
    knowledgeMode.value === 'SELECTED'
      ? listKnowledgeBases(projects.state.selectedId, kbPage.value, kbQuery.value, '', signal)
      : Promise.resolve(null),
);
const documents = useResource(
  () => `${knowledgeMode.value}:${docKbId.value}:${docPage.value}`,
  (signal) =>
    knowledgeMode.value === 'SELECTED' && docKbId.value
      ? listDocuments(projects.state.selectedId, docKbId.value, docPage.value, signal)
      : Promise.resolve(null),
);
const busy = computed(() => model.active || model.state.sending || model.state.loading);
const canSend = computed(
  () =>
    !!model.state.conversation &&
    !busy.value &&
    !!question.value.trim() &&
    !source.loading.value &&
    !source.error.value &&
    (knowledgeMode.value === 'NONE' || selectedKbs.value.length > 0) &&
    (!runInput.value || selectedRun.value?.id === runInput.value),
);
const showCurrent = computed(
  () =>
    model.state.turn &&
    (model.active ||
      !model.state.messages.some(
        (message) =>
          message.role === 'ASSISTANT' && message.answer && message.turnId === model.state.turn?.id,
      )),
);
function toggleKb(kb: KnowledgeBase) {
  if (selectedKbs.value.some((item) => item.id === kb.id)) {
    selectedKbs.value = selectedKbs.value.filter((item) => item.id !== kb.id);
    selectedDocs.value = selectedDocs.value.filter((item) => item.knowledgeBaseId !== kb.id);
  } else if (kb.readyCount && selectedKbs.value.length < 5)
    selectedKbs.value = [...selectedKbs.value, kb];
}
function toggleDoc(document: Document) {
  if (selectedDocs.value.some((item) => item.id === document.id))
    selectedDocs.value = selectedDocs.value.filter((item) => item.id !== document.id);
  else if (document.status === 'READY' && selectedDocs.value.length < 20)
    selectedDocs.value = [...selectedDocs.value, document];
}
function verifyRun() {
  if (!runInput.value) {
    selectedRun.value = null;
    return;
  }
  void scopeAction.run(
    (signal) => getRun(projects.state.selectedId, runInput.value, signal),
    (value) => {
      selectedRun.value = value;
      Object.assign(testContext, value.testContext);
    },
  );
}
function create() {
  const body = {
    projectId: projects.state.selectedId,
    ...(newTitle.value.trim() ? { title: newTitle.value.trim() } : {}),
  };
  void action.run(
    (signal) => createConversation(body, key(JSON.stringify(body)), signal),
    async (value) => {
      await router.push({
        path: '/assistant/' + value.id,
        query: { ...route.query, projectId: body.projectId },
      });
    },
  );
}
function askRemove(id: string, title: string) {
  removal.value = { id, title };
  dialog.value?.showModal();
}
function confirmRemove() {
  const target = removal.value;
  if (!target) return;
  void action.run(
    (signal) => deleteConversation(target.id, signal),
    async () => {
      dialog.value?.close();
      removal.value = null;
      if (conversationId.value === target.id) {
        model.reset();
        await router.push({ path: '/assistant', query: { projectId: projects.state.selectedId } });
      } else await conversations.reload();
    },
  );
}
function messageContext(message: Message) {
  return (
    message.context ??
    model.state.messages.find((item) => item.role === 'USER' && item.turnId === message.turnId)
      ?.context ??
    null
  );
}
async function send() {
  if (!canSend.value) return;
  formError.value = '';
  const context: MessageContext = {
    testContext: { ...testContext },
    knowledgeMode: knowledgeMode.value,
    knowledgeBaseIds:
      knowledgeMode.value === 'SELECTED' ? selectedKbs.value.map((kb) => kb.id) : [],
    documentIds:
      knowledgeMode.value === 'SELECTED' ? selectedDocs.value.map((document) => document.id) : [],
    selectionId: selectionId.value || null,
    runId: runInput.value ? (selectedRun.value?.id ?? null) : null,
  };
  if (await model.send(question.value.trim(), context)) question.value = '';
}
function enter(event: KeyboardEvent) {
  if (event.isComposing || event.keyCode === 229 || event.shiftKey) return;
  event.preventDefault();
  void send();
}
function clearSource() {
  resetContext();
  void router.replace({ path: route.path, query: { projectId: projects.state.selectedId } });
}
</script>
<template>
  <div class="flow-page assistant-page">
    <header class="page-heading-row">
      <div>
        <p class="section-kicker">ASSISTANT / 基于事实与依据</p>
        <h1 data-page-heading tabindex="-1">AI 助手</h1>
        <p class="muted">显式选择资料与测试范围，核对来源后再处理业务建议。</p>
      </div>
    </header>
    <div class="assistant-layout">
      <details open class="panel conversation-navigation">
        <summary>会话记录</summary>
        <div class="conversation-controls">
          <label
            >新会话名称<input
              v-model="newTitle"
              maxlength="128"
              placeholder="可选"
              :disabled="action.busy.value" /></label
          ><button class="button button-primary" :disabled="action.busy.value" @click="create">
            新建会话</button
          ><label
            >搜索会话<input
              v-model="search"
              type="search"
              @keyup.enter="query = search.trim()" /></label
          ><button class="text-button" @click="query = search.trim()">搜索</button>
        </div>
        <RequestState
          :loading="conversations.loading.value"
          :error="conversations.error.value"
          :trace="conversations.trace.value"
          @retry="conversations.reload"
        /><template v-if="conversations.data.value"
          ><p v-if="!conversations.data.value.items.length" class="flow-empty">
            暂无会话，创建一个会话开始分析。
          </p>
          <ul class="conversation-list">
            <li
              v-for="item in conversations.data.value.items"
              :key="item.id"
              :class="{ selected: item.id === conversationId }"
            >
              <RouterLink
                :to="{
                  path: '/assistant/' + item.id,
                  query: { projectId: projects.state.selectedId },
                }"
                >{{ item.title || '未命名会话'
                }}<small>{{ new Date(item.updatedAt).toLocaleString('zh-CN') }}</small></RouterLink
              ><button
                class="text-button"
                :aria-label="'删除会话 ' + (item.title || '未命名会话')"
                :disabled="action.busy.value"
                @click="askRemove(item.id, item.title)"
              >
                删除
              </button>
            </li>
          </ul>
          <PageControls
            :page="page"
            :size="20"
            :total="conversations.data.value.total"
            @change="page = $event"
        /></template>
        <p v-if="action.error.value" class="flow-error" role="alert">{{ action.error.value }}</p>
      </details>
      <section class="assistant-workspace" aria-label="会话内容">
        <div class="panel flow-panel">
          <h2>{{ model.state.conversation?.title || '新分析' }}</h2>
          <p v-if="!conversationId" class="muted">
            先新建会话，再选择上下文并提问。打开助手不会自动发送问题。
          </p>
          <RequestState :loading="model.state.loading" />
          <div v-if="model.state.error" class="flow-error" role="alert">
            <p>{{ model.state.error }}</p>
            <small v-if="model.state.trace">请求编号：{{ model.state.trace }}</small
            ><button
              v-if="conversationId && !model.state.conversation"
              class="text-button"
              @click="model.load(conversationId)"
            >
              重新读取会话</button
            ><button
              v-else-if="model.state.conversation"
              class="text-button"
              @click="model.reloadMessages()"
            >
              读取会话记录
            </button>
          </div>
          <p v-if="model.state.notice" class="notice" role="status">{{ model.state.notice }}</p>
          <div v-if="model.state.conversation" class="message-list">
            <p v-if="!model.state.messages.length && !model.state.turn" class="flow-empty">
              可以询问测试用例、规范依据或选定运行的结果。
            </p>
            <template v-for="message in model.state.messages" :key="message.id"
              ><article v-if="message.role === 'USER'" class="user-message">
                <span class="section-kicker">我的问题</span>
                <p class="preserve-text">{{ message.text }}</p>
                <small v-if="message.context"
                  >{{
                    message.context.knowledgeMode === 'NONE'
                      ? '不使用知识资料'
                      : message.context.knowledgeBaseIds.length + ' 个资料库'
                  }}{{ message.context.runId ? ' · 运行 #' + message.context.runId : ''
                  }}{{ message.context.selectionId ? ' · 指定用例范围' : '' }}</small
                >
              </article>
              <AnswerCard
                v-else-if="message.answer"
                :answer="message.answer"
                :context="messageContext(message)"
                @citation="citationId = $event" />
              <article v-else class="answer-card">
                <span class="section-kicker">AI / 消息记录</span
                ><SafeMarkdown :text="message.text" /></article
            ></template>
          </div>
          <article v-if="showCurrent" class="answer-card live-answer" :aria-busy="model.active">
            <template v-if="model.state.answer"
              ><AnswerCard
                :answer="model.state.answer"
                :context="model.state.context"
                @citation="citationId = $event" /></template
            ><template v-else
              ><div class="panel-heading">
                <span class="section-kicker">{{
                  model.active ? '正在处理本轮问题' : '本轮尚无最终回答'
                }}</span><span v-if="model.state.connected" class="status-tag">{{
                  model.state.reconnecting ? '正在重连' : '接收中'
                }}</span>
              </div>
              <p v-if="model.state.stage" role="status">{{ model.state.stage }}</p>
              <ul v-if="model.state.tools.length" class="tool-steps">
                <li v-for="tool in model.state.tools" :key="tool.id">
                  <strong>{{ tool.label }}</strong><span class="status-tag">{{
                    { RUNNING: '进行中', SUCCEEDED: '已完成', FAILED: '失败' }[
                      tool.status as 'RUNNING' | 'SUCCEEDED' | 'FAILED'
                    ]
                  }}</span>
                  <p v-if="tool.summary">{{ tool.summary }}</p>
                </li>
              </ul>
              <SafeMarkdown v-if="model.state.delta" :text="model.state.delta" />
              <p v-else class="muted">
                {{ model.active ? '等待本轮回答，可随时停止。' : '未形成可保存的业务建议。' }}
              </p></template
            >
          </article>
          <div v-if="model.active" class="flow-actions">
            <button
              class="button button-secondary"
              :disabled="model.state.cancelling"
              @click="model.stop()"
            >
              {{ model.state.cancelling ? '正在停止…' : '停止回答' }}</button><button
              v-if="!model.state.connected"
              class="button button-secondary"
              @click="model.reconnect()"
            >
              重新连接本轮回答
            </button>
          </div>
          <PageControls
            v-if="model.state.total > 20"
            :page="model.state.page"
            :size="20"
            :total="model.state.total"
            @change="model.reloadMessages($event)"
          />
        </div>
        <form class="panel flow-panel assistant-composer" @submit.prevent="send">
          <details class="question-context" :open="!!source.error.value">
            <summary>
              问题上下文 ·
              {{ knowledgeMode === 'NONE' ? '不使用知识资料' : selectedKbs.length + ' 个资料库'
              }}{{ runInput ? ' · 运行 #' + runInput : ''
              }}{{ selectionId ? ' · 指定用例范围' : '' }}
            </summary>
            <RequestState
              :loading="source.loading.value"
              :error="source.error.value"
              :trace="source.trace.value"
              @retry="source.reload"
            /><button
              v-if="source.error.value"
              class="text-button"
              type="button"
              @click="clearSource"
            >
              清除附带范围
            </button>
            <fieldset :disabled="busy || !model.state.conversation" class="context-fields">
              <div class="form-grid">
                <label
                  >表型<select v-model="testContext.deviceType" aria-label="表型">
                    <option value="UNSPECIFIED">未指定</option>
                    <option value="SINGLE_PHASE">单相表</option>
                    <option value="THREE_PHASE">三相表</option>
                  </select></label
                ><label>产品型号<input v-model="testContext.productModel" maxlength="128" /></label
                ><label
                  >固件版本<input v-model="testContext.firmwareVersion" maxlength="64" /></label
                ><label
                  >硬件版本<input v-model="testContext.hardwareVersion" maxlength="64" /></label
                ><label>规约名称<input v-model="testContext.protocolName" maxlength="128" /></label
                ><label
                  >规约版本<input v-model="testContext.protocolVersion" maxlength="64"
                /></label>
              </div>
              <label
                >资料使用范围<select v-model="knowledgeMode" aria-label="资料使用范围">
                  <option value="NONE">不使用知识资料</option>
                  <option value="SELECTED">使用指定知识资料</option>
                </select></label
              >
              <section v-if="knowledgeMode === 'SELECTED'" class="context-library-picker">
                <p class="muted">
                  最多选择 5 个库、20
                  份就绪文档。未选文档时使用所选库内的就绪资料；选择文档后仅使用这些文档。
                </p>
                <div class="flow-toolbar">
                  <label>查找资料库<input v-model="kbSearch" /></label
                  ><button
                    class="button button-secondary"
                    type="button"
                    @click="
                      kbPage = 0;
                      kbQuery = kbSearch.trim();
                    "
                  >
                    查找
                  </button>
                </div>
                <RequestState
                  :loading="libraries.loading.value"
                  :error="libraries.error.value"
                  @retry="libraries.reload"
                />
                <div v-if="libraries.data.value" class="context-options">
                  <label v-for="kb in libraries.data.value.items" :key="kb.id" class="flow-check"
                    ><input
                      type="checkbox"
                      :checked="selectedKbs.some((item) => item.id === kb.id)"
                      :disabled="
                        !kb.readyCount ||
                        (!selectedKbs.some((item) => item.id === kb.id) && selectedKbs.length >= 5)
                      "
                      @change="toggleKb(kb)"
                    />{{ kb.name }} · {{ kb.readyCount }} 份就绪</label
                  ><PageControls
                    :page="kbPage"
                    :size="20"
                    :total="libraries.data.value.total"
                    @change="kbPage = $event"
                  />
                </div>
                <div class="context-chips">
                  <button
                    v-for="kb in selectedKbs"
                    :key="kb.id"
                    class="context-chip"
                    type="button"
                    @click="toggleKb(kb)"
                  >
                    {{ kb.name }} ×
                  </button>
                </div>
                <template v-if="selectedKbs.length"
                  ><label
                    >展开文档列表<select v-model="docKbId" aria-label="展开文档列表">
                      <option v-for="kb in selectedKbs" :key="kb.id" :value="kb.id">
                        {{ kb.name }}
                      </option>
                    </select></label
                  ><RequestState
                    :loading="documents.loading.value"
                    :error="documents.error.value"
                    @retry="documents.reload" />
                  <div v-if="documents.data.value" class="context-options">
                    <label
                      v-for="document in documents.data.value.items"
                      :key="document.id"
                      class="flow-check"
                      ><input
                        type="checkbox"
                        :checked="selectedDocs.some((item) => item.id === document.id)"
                        :disabled="
                          document.status !== 'READY' ||
                          (!selectedDocs.some((item) => item.id === document.id) &&
                            selectedDocs.length >= 20)
                        "
                        @change="toggleDoc(document)"
                      />{{ document.title }} · {{ document.versionLabel
                      }}{{ document.status !== 'READY' ? '（尚未就绪）' : '' }}</label
                    ><PageControls
                      :page="docPage"
                      :size="20"
                      :total="documents.data.value.total"
                      @change="docPage = $event"
                    /></div
                ></template>
                <div class="context-chips">
                  <button
                    v-for="document in selectedDocs"
                    :key="document.id"
                    class="context-chip"
                    type="button"
                    @click="toggleDoc(document)"
                  >
                    {{ document.title }} ×
                  </button>
                </div>
              </section>
              <div class="flow-toolbar">
                <label
                  >运行 ID（可选）<input
                    v-model="runInput"
                    inputmode="numeric"
                    pattern="[1-9][0-9]{0,18}" /></label
                ><button
                  class="button button-secondary"
                  type="button"
                  :disabled="scopeAction.busy.value"
                  @click="verifyRun"
                >
                  确认运行范围</button
                ><button
                  v-if="runInput"
                  class="text-button"
                  type="button"
                  @click="
                    runInput = '';
                    selectedRun = null;
                  "
                >
                  移除运行
                </button>
              </div>
              <p v-if="selectedRun && runInput === selectedRun.id" class="notice">
                已选择：{{ selectedRun.name }}
              </p>
              <p v-if="scopeAction.error.value" class="flow-error" role="alert">
                {{ scopeAction.error.value }}
              </p>
              <p v-if="selectionId" class="notice">
                已附带业务页的用例选择范围。<button
                  class="text-button"
                  type="button"
                  @click="selectionId = ''"
                >
                  移除用例范围
                </button>
              </p>
            </fieldset>
          </details>
          <label class="composer-label"
            >输入问题<textarea
              v-model="question"
              maxlength="8000"
              rows="4"
              :disabled="!model.state.conversation || busy"
              placeholder="例如：核对本次运行的失败结果，并说明还缺少哪些依据"
              @keydown.enter="enter"
            />
          </label>
          <p v-if="formError" class="flow-error" role="alert">{{ formError }}</p>
          <div class="flow-toolbar">
            <small class="muted">Enter 发送 · Shift + Enter 换行；建议需人工检查。</small
            ><button class="button button-primary" type="submit" :disabled="!canSend">
              {{ model.state.sending ? '正在发送…' : '发送问题' }}
            </button>
          </div>
        </form>
      </section>
    </div>
    <CitationDialog :citation-id="citationId" @close="citationId = ''" />
    <dialog
      ref="dialog"
      class="flow-dialog"
      aria-labelledby="conversation-delete-title"
      @close="removal = null"
    >
      <h2 id="conversation-delete-title">删除会话？</h2>
      <p>“{{ removal?.title }}”的活动回答将先停止，已保存的业务用例不会删除。</p>
      <p v-if="action.error.value" class="flow-error" role="alert">{{ action.error.value }}</p>
      <div class="flow-actions">
        <button
          class="button button-secondary"
          :disabled="action.busy.value"
          @click="dialog?.close()"
        >
          取消</button
        ><button class="button button-primary" :disabled="action.busy.value" @click="confirmRemove">
          确认删除会话
        </button>
      </div>
    </dialog>
  </div>
</template>
