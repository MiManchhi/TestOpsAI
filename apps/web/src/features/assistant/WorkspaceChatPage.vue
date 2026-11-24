<script setup lang="ts">
import { nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { errorMessage, ApiError } from '../../app/http';
import type {
  ChatCitation,
  ChatConversation,
  ChatMessage,
  ChatTurn,
} from '../../app/workspace-contracts';
import { getVersion, uploadAttachment } from '../knowledge/workspace-api';
import { carryFromChat } from '../workspace/chat-handoff';
import AppIcon from '../../app/AppIcon.vue';
import SafeMarkdown from './SafeMarkdown.vue';
import * as api from './workspace-chat';

const route = useRoute();
const router = useRouter();
const rows = ref<ChatConversation[]>([]);
const thread = ref<ChatMessage[]>([]);
const currentTurn = ref<ChatTurn>();
const input = ref('');
const query = ref('');
const files = ref<File[]>([]);
const busy = ref(false);
const loadingHistory = ref(false);
const status = ref('');
const historyError = ref('');
const messageError = ref('');
const available = ref(true);
const drawer = ref(false);
const source = ref<ChatCitation>();
const sourceTarget = ref('');
const verdicts = {
  MEETS: '达标',
  DOES_NOT_MEET: '未达标',
  INSUFFICIENT: '证据不足',
  NEEDS_CONFIRMATION: '需人工确认',
};
const labels = { AT_RUN: '当时依据', CURRENT: '当前依据', DESIGN: '用例评审' };
const sourceError = ref('');
const sourceLoading = ref(false);
const readiness = ref<api.AssistantStatus>();
const statusUnavailable = ref(false);
let capabilityRequest: AbortController | undefined;
async function checkService() {
  capabilityRequest?.abort();
  const attempt = new AbortController();
  capabilityRequest = attempt;
  readiness.value = undefined;
  statusUnavailable.value = false;
  try {
    const value = await api.assistantStatus(project(), attempt.signal);
    if (!attempt.signal.aborted) readiness.value = value;
  } catch {
    if (!attempt.signal.aborted) statusUnavailable.value = true;
  }
}
const moreHistory = ref(false);
const historyPage = ref(0);
const historyInput = ref<HTMLInputElement>();
const transcript = ref<HTMLElement>();
const starterQuestions = [
  {
    label: '核对最近复测',
    icon: 'activity',
    text: '请核对本项目最近一次复测，分别说明 Robot 执行结果和验收条款判定。',
  },
  {
    label: '评审我的用例',
    icon: 'check',
    text: '请评审下面的 Robot 用例，指出已覆盖的条款、漏测项和预期结果。\n\n',
  },
  {
    label: '编写 Robot 用例',
    icon: 'code',
    text: '请根据下面的验收要求编写可复制的 Robot 用例，并列出条款出处和待确认项。\n\n',
  },
] as const;
function compose(text: string) {
  input.value = text;
  edited();
  inputBox.value?.focus();
}
function candidateId(text: string) {
  return /^运行\s*#([1-9][0-9]{0,18})(?:\s|·|$)/u.exec(text.trim())?.[1];
}
async function chooseRun(text: string) {
  const id = candidateId(text);
  if (!id || busy.value || isPending(currentTurn.value)) return;
  compose('核对运行 #' + id);
  await send(id);
}
const inputBox = ref<HTMLTextAreaElement>();
const sourceDialog = ref<HTMLDialogElement>();
let returnFocus: HTMLElement | undefined;
let historyRequest: AbortController | undefined;
let pageRequest = new AbortController();
let timer: ReturnType<typeof setTimeout> | undefined;
let pendingKey: string | undefined;
let attachmentIds: string[] = [];
let preserveNavigation = false;
let trackingTurn = '';

const project = () => String(route.params.projectId);
const conversation = () => String(route.params.conversationId ?? '');
const linkedRun = () =>
  typeof route.query.runId === 'string' && /^[1-9][0-9]{0,18}$/.test(route.query.runId)
    ? route.query.runId
    : '';
const handoff = (turn: string) =>
  carryFromChat(
    conversation(),
    thread.value.find((item) => item.author === 'USER' && item.turnId === turn)?.attachments ?? [],
  );
const home = () => '/p/' + project() + '/chat';
const isPending = (state?: ChatTurn) =>
  Boolean(state && ['QUEUED', 'RUNNING'].includes(state.status));

async function loadHistory(page = 0) {
  historyRequest?.abort();
  historyRequest = new AbortController();
  const { signal } = historyRequest;
  historyError.value = '';
  loadingHistory.value = true;
  try {
    const result = await api.history(project(), page, query.value, signal);
    if (signal.aborted) return;
    rows.value = page ? [...rows.value, ...result.items] : result.items;
    historyPage.value = page;
    moreHistory.value = (result.page + 1) * result.size < result.total;
    available.value = true;
  } catch (cause) {
    if (signal.aborted) return;
    available.value = !(cause instanceof ApiError && cause.status === 404);
    historyError.value = available.value ? errorMessage(cause) : '当前服务尚未开放 AI 对话。';
  } finally {
    if (!signal.aborted) loadingHistory.value = false;
  }
}
async function loadThread(signal: AbortSignal) {
  const id = conversation();
  if (!id) return;
  try {
    const all: ChatMessage[] = [];
    for (let page = 0; ; page++) {
      const result = await api.messages(project(), id, page, signal);
      if (signal.aborted) return;
      all.push(...result.items);
      if (all.length >= result.total || !result.items.length) break;
    }
    const box = transcript.value;
    const atBottom = !box || box.scrollHeight - box.scrollTop - box.clientHeight < 100;
    thread.value = all;
    if (atBottom) {
      await nextTick();
      if (transcript.value && !signal.aborted)
        transcript.value.scrollTop = transcript.value.scrollHeight;
    }
    return all.at(-1)?.turnId;
  } catch (cause) {
    if (!signal.aborted) {
      thread.value = [];
      messageError.value = errorMessage(cause);
    }
  }
}
async function followTurn(id: string, signal: AbortSignal, after = 0, retries = 0) {
  let cursor = after;
  if (after === 0 && retries === 0) trackingTurn = id;
  try {
    const result = await api.turn(project(), id, signal);
    if (signal.aborted || trackingTurn !== id) return;
    currentTurn.value = result;
    if (isPending(result)) {
      status.value = '正在查找可用资料并整理回答…';
      try {
        const ended = await api.watchTurn(project(), id, cursor, signal, (event) => {
          cursor = event.sequence;
          if (event.event === 'turn.failed')
            status.value = event.data.error?.message || '回答未完成。';
        });
        if (signal.aborted || trackingTurn !== id) return;
        if (ended) {
          await followTurn(id, signal, cursor, retries);
          return;
        }
      } catch (cause) {
        if (signal.aborted || trackingTurn !== id) return;
        if (retries >= 3) throw cause;
      }
      if (retries >= 3) throw new Error('回答连接已中断。刷新会话可以查看已保存的回答。');
      timer = setTimeout(() => void followTurn(id, signal, cursor, retries + 1), 850);
    } else {
      status.value =
        result.status === 'FAILED'
          ? status.value && !status.value.startsWith('正在')
            ? status.value
            : '回答未完成，请检查模型配置后重试。'
          : result.status === 'CANCELLED'
            ? '已停止回答。'
            : '';
      await loadThread(signal);
    }
  } catch (cause) {
    if (!signal.aborted && trackingTurn === id) {
      status.value = '';
      messageError.value = errorMessage(cause);
    }
  }
}
function reset() {
  pageRequest.abort();
  pageRequest = new AbortController();
  clearTimeout(timer);
  trackingTurn = '';
  thread.value = [];
  input.value = '';
  files.value = [];
  currentTurn.value = undefined;
  busy.value = false;
  status.value = '';
  messageError.value = '';
  pendingKey = undefined;
  attachmentIds = [];
  sourceDialog.value?.close();
}
watch(
  () => route.params.projectId,
  () => {
    query.value = '';
    rows.value = [];
    available.value = true;
    drawer.value = false;
    void loadHistory();
    void checkService();
  },
  { immediate: true },
);
watch(query, () => void loadHistory());
watch(
  () => [route.params.projectId, route.params.conversationId],
  async () => {
    drawer.value = false;
    if (preserveNavigation) return;
    reset();
    const { signal } = pageRequest;
    const turnId = await loadThread(signal);
    if (turnId && !signal.aborted) void followTurn(turnId, signal);
  },
  { immediate: true },
);
function refreshVisibleThread() {
  if (document.visibilityState !== 'visible') return;
  sourceDialog.value?.close();
  const signal = pageRequest.signal;
  void loadHistory();
  if (conversation()) void loadThread(signal);
}
onMounted(() => document.addEventListener('visibilitychange', refreshVisibleThread));
onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', refreshVisibleThread);
  capabilityRequest?.abort();
  pageRequest.abort();
  historyRequest?.abort();
  clearTimeout(timer);
});
async function newChat() {
  drawer.value = false;
  if (conversation()) await router.push(home());
  else reset();
  await nextTick();
  inputBox.value?.focus();
}
async function openHistory() {
  drawer.value = !drawer.value;
  if (drawer.value) {
    await nextTick();
    historyInput.value?.focus();
  }
}
function pick(event: Event) {
  const control = event.target as HTMLInputElement;
  files.value = Array.from(control.files ?? []);
  control.value = '';
  pendingKey = undefined;
  attachmentIds = [];
}
function edited() {
  pendingKey = undefined;
  attachmentIds = [];
}
async function send(selectedRun?: string) {
  if (
    !available.value ||
    busy.value ||
    isPending(currentTurn.value) ||
    (!input.value.trim() && !files.value.length)
  )
    return;
  const { signal } = pageRequest;
  const projectId = project();
  const text = input.value.trim();
  const attachments = [...files.value];
  const key = (pendingKey ??= crypto.randomUUID());
  busy.value = true;
  messageError.value = '';
  try {
    for (const file of attachments.slice(attachmentIds.length)) {
      const result = await uploadAttachment(projectId, file, crypto.randomUUID(), signal);
      if (signal.aborted) return;
      attachmentIds.push(result.attachmentId);
    }
    const result = await api.start(
      projectId,
      {
        text,
        clientMessageId: key,
        attachmentIds,
        ...(selectedRun || linkedRun() ? { runId: selectedRun || linkedRun() } : {}),
        ...(conversation() ? { conversationId: conversation() } : {}),
      },
      key,
      signal,
    );
    if (signal.aborted) return;
    input.value = '';
    files.value = [];
    edited();
    currentTurn.value = result;
    trackingTurn = result.turnId;
    if (!conversation()) {
      preserveNavigation = true;
      try {
        await router.push({
          path: home() + '/' + result.conversationId,
          query: linkedRun() ? { runId: linkedRun() } : {},
        });
      } finally {
        preserveNavigation = false;
      }
    }
    if (signal.aborted) return;
    await loadThread(signal);
    void loadHistory();
    void followTurn(result.turnId, signal);
  } catch (cause) {
    if (!signal.aborted) messageError.value = errorMessage(cause);
  } finally {
    if (!signal.aborted) busy.value = false;
  }
}
function keyboard(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && event.keyCode !== 229) {
    event.preventDefault();
    void send();
  }
}
async function stop() {
  if (!currentTurn.value) return;
  const { signal } = pageRequest;
  try {
    currentTurn.value = await api.cancel(project(), currentTurn.value.turnId, signal);
  } catch (cause) {
    if (!signal.aborted) messageError.value = errorMessage(cause);
  }
}
async function openSource(id: string, event: MouseEvent) {
  returnFocus = event.currentTarget as HTMLElement;
  source.value = undefined;
  sourceTarget.value = '';
  sourceError.value = '';
  sourceLoading.value = true;
  sourceDialog.value?.showModal();
  const { signal } = pageRequest;
  try {
    const result = await api.citation(project(), id, signal);
    if (signal.aborted) return;
    source.value = result;
    if (result.projectSource?.kind === 'DOCUMENT') {
      const doc = await getVersion(project(), result.projectSource.versionId, signal);
      if (!signal.aborted)
        sourceTarget.value =
          '/p/' +
          project() +
          '/documents/' +
          doc.documentId +
          '/versions/' +
          doc.versionId +
          '?locator=' +
          encodeURIComponent(result.projectSource.locator) +
          '&citationId=' +
          result.citationId;
    } else if (result.projectSource?.kind === 'RUN_LOG') {
      const location = result.projectSource;
      sourceTarget.value =
        '/p/' +
        project() +
        '/runs/' +
        location.runId +
        '?artifactId=' +
        location.artifactId +
        '&fromLine=' +
        location.fromLine;
    }
  } catch (cause) {
    if (!signal.aborted) sourceError.value = errorMessage(cause);
  } finally {
    if (!signal.aborted) sourceLoading.value = false;
  }
}
async function copyAnswer(id: string, draftOnly = false) {
  const { signal } = pageRequest;
  messageError.value = '';
  await loadThread(signal);
  if (signal.aborted) return;
  const answer = thread.value.find((message) => message.messageId === id)?.answer;
  if (!answer) {
    messageError.value = '当前无法复制这条回答，请重新核对权限。';
    return;
  }
  try {
    const location = answer.citations.map((entry) => entry.label + ' · ' + entry.excerpt);
    const decisions = answer.findings.map((item) =>
      [item.versionLabel, verdicts[item.verdict], item.expected, item.observed]
        .filter(Boolean)
        .join(' · '),
    );
    await navigator.clipboard.writeText(
      draftOnly && answer.robotDraft
        ? answer.robotDraft
        : [answer.summary, ...decisions, answer.robotDraft, ...answer.pending, ...location]
            .filter(Boolean)
            .join('\n'),
    );
    if (!signal.aborted) status.value = draftOnly ? '已复制 Robot 代码。' : '已复制回答及出处。';
  } catch (cause) {
    if (!signal.aborted) messageError.value = errorMessage(cause);
  }
}
</script>

<template>
  <div class="chat">
    <aside class="history" :class="{ 'is-open': drawer }" aria-label="个人对话">
      <div class="history-heading">
        个人对话
        <button
          v-if="drawer"
          class="icon-button dismiss"
          aria-label="关闭历史记录"
          @click="drawer = false"
        >
          <AppIcon name="close" />
        </button>
      </div>
      <button class="new-chat button" @click="newChat"><AppIcon name="plus" />新对话</button>
      <input
        ref="historyInput"
        v-model="query"
        type="search"
        aria-label="搜索个人对话"
        placeholder="搜索对话"
      />
      <small>我的对话</small>
      <p v-if="historyError" role="status">{{ historyError }}</p>
      <p v-else-if="!rows.length">
        {{
          loadingHistory
            ? '正在读取…'
            : query
              ? '没有匹配的对话'
              : '第一条消息发送后，对话将保存在这里。'
        }}
      </p>
      <RouterLink
        v-for="row in rows"
        :key="row.conversationId"
        :to="home() + '/' + row.conversationId"
      >
        {{ row.title }}
      </RouterLink>
      <button
        v-if="moreHistory"
        class="button button-secondary"
        @click="loadHistory(historyPage + 1)"
      >
        更多对话
      </button>
    </aside>
    <section class="thread" :class="{ empty: !thread.length }" aria-label="聊天">
      <button
        class="button button-secondary history-toggle"
        :aria-expanded="drawer"
        @click="openHistory"
      >
        个人对话
      </button>
      <header v-if="!thread.length" class="welcome">
        <div class="assistant-symbol"><AppIcon name="activity" /></div>
        <h1 data-page-heading tabindex="-1">今天想核对什么？</h1>
        <p>直接提问，或附上用例与日志。回答会引用你可查看的项目资料。</p>
      </header>
      <div v-else ref="transcript" class="message-list" aria-label="对话消息">
        <article
          v-for="item in thread"
          :key="item.messageId"
          :class="item.author === 'USER' ? 'bubble' : 'reply'"
        >
          <template v-if="item.answer">
            <div class="byline"><AppIcon name="activity" />测试助手</div>
            <SafeMarkdown :text="item.answer.summary" />
            <section
              v-for="(finding, index) in item.answer.findings"
              :key="index"
              class="finding"
              :data-verdict="finding.verdict"
            >
              <strong
                >{{ labels[finding.basis] }} · {{ finding.versionLabel }} ·
                {{ verdicts[finding.verdict] }}</strong
              >
              <p>
                {{ finding.expected }}<span v-if="finding.observed"> · {{ finding.observed }}</span>
              </p>
              <template v-for="entry in item.answer.citations" :key="entry.citationId">
                <button
                  v-if="finding.citationIds.includes(entry.citationId)"
                  class="text-button source-button"
                  @click="openSource(entry.citationId, $event)"
                >
                  {{ entry.label }} ↗
                </button>
              </template>
            </section>
            <section v-if="item.answer.robotDraft" class="code-panel">
              <header>
                <AppIcon name="code" />Robot Framework<button
                  class="text-button"
                  @click="copyAnswer(item.messageId, true)"
                >
                  复制代码
                </button>
              </header>
              <pre><code>{{ item.answer.robotDraft }}</code></pre>
            </section>
            <ul v-if="item.answer.pending.length">
              <li v-for="(pending, index) in item.answer.pending" :key="index">
                <button
                  v-if="candidateId(pending)"
                  class="button button-secondary"
                  :disabled="busy || isPending(currentTurn)"
                  @click="chooseRun(pending)"
                >
                  {{ pending }}</button><span v-else>{{ pending }}</span>
              </li>
            </ul>
            <button
              v-for="entry in item.answer.citations.filter(
                (proof) =>
                  !item.answer?.findings.some((finding) =>
                    finding.citationIds.includes(proof.citationId),
                  ),
              )"
              :key="entry.citationId"
              class="text-button source-button"
              @click="openSource(entry.citationId, $event)"
            >
              {{ entry.label }} ↗
            </button>
            <div class="message-actions">
              <button class="button button-secondary" @click="copyAnswer(item.messageId)">
                {{ item.answer.robotDraft ? '复制草稿及出处' : '复制回答及出处' }}
              </button>
              <RouterLink
                v-if="handoff(item.turnId).run.output || handoff(item.turnId).run.logs?.length"
                :to="{ path: '/p/' + project() + '/runs/new', query: handoff(item.turnId).run }"
                >去登记复测</RouterLink
              >
              <RouterLink
                v-if="handoff(item.turnId).document.attachmentId"
                :to="{
                  path: '/p/' + project() + '/documents/new',
                  query: handoff(item.turnId).document,
                }"
                >去提交资料</RouterLink
              >
            </div>
          </template>
          <p v-else>{{ item.text }}</p>
          <small v-for="file in item.attachments" :key="file.attachmentId">{{
            file.fileName
          }}</small>
        </article>
      </div>
      <p v-if="status" class="state" role="status">{{ status }}</p>
      <p v-if="messageError" class="state" role="alert">{{ messageError }}</p>
      <p v-if="!available" class="state" role="status">当前服务尚未开放 AI 对话。</p>
      <RouterLink v-if="linkedRun()" :to="'/p/' + project() + '/runs/' + linkedRun()"
        >查看关联运行</RouterLink
      >
      <aside
        v-if="
          statusUnavailable ||
          (readiness && (readiness.chat !== 'READY' || readiness.embedding !== 'READY'))
        "
        class="capability"
        aria-live="polite"
      >
        <div>
          <strong v-if="statusUnavailable">暂时无法确认模型状态</strong
          ><template v-else
            ><span v-if="readiness?.chat !== 'READY'">{{
              readiness?.chat === 'UNCONFIGURED'
                ? '聊天模型待配置，生成和追问暂不可用。'
                : '聊天模型暂不可用。'
            }}</span
            ><span v-if="readiness?.embedding !== 'READY'">{{
              readiness?.embedding === 'UNCONFIGURED'
                ? '知识检索模型待配置。'
                : '知识检索暂不可用。'
            }}</span></template
          >
          <small>可查看历史对话和运行记录。模型接入由管理员配置。</small>
        </div>
        <button type="button" class="text-button" @click="checkService">重新检查</button>
      </aside>
      <form class="composer" :aria-busy="busy" @submit.prevent="send()">
        <textarea
          ref="inputBox"
          v-model="input"
          maxlength="20000"
          rows="3"
          aria-label="消息"
          placeholder="提问、粘贴 Robot 用例，或附上运行日志…"
          :disabled="busy || !available"
          @input="edited"
          @keydown="keyboard"
        />
        <div v-if="files.length" class="files">
          <span v-for="file in files" :key="file.name">{{ file.name }}</span>
          <button
            class="text-button"
            type="button"
            :disabled="busy"
            @click="
              files = [];
              edited();
            "
          >
            移除附件
          </button>
        </div>
        <div class="composer-actions">
          <label class="attachment"
            ><AppIcon name="paperclip" /> 添加附件
            <input
              type="file"
              aria-label="添加附件"
              accept=".robot,.resource,.xml,.txt,.log,.md,.markdown"
              multiple
              :disabled="busy || !available"
              @change="pick"
            />
          </label>
          <button
            v-if="isPending(currentTurn)"
            type="button"
            class="button button-secondary"
            @click="stop"
          >
            停止
          </button>
          <button
            v-else
            class="button button-primary"
            type="submit"
            :disabled="busy || !available || (!input.trim() && !files.length)"
          >
            发送 <AppIcon name="send" />
          </button>
        </div>
      </form>
      <div v-if="!thread.length && !conversation()" class="starters">
        <button
          v-for="item in starterQuestions"
          :key="item.label"
          :disabled="busy"
          @click="compose(item.text)"
        >
          <AppIcon :name="item.icon" />{{ item.label }}
        </button>
      </div>
    </section>
    <dialog
      ref="sourceDialog"
      class="source-drawer"
      aria-labelledby="source-title"
      @close="returnFocus?.focus()"
    >
      <div class="composer-actions">
        <h2 id="source-title">来源</h2>
        <button class="text-button" @click="sourceDialog?.close()">关闭</button>
      </div>
      <p v-if="sourceLoading" role="status">正在读取来源…</p>
      <p v-else-if="sourceError" role="alert">{{ sourceError }}</p>
      <template v-else-if="source">
        <h3>{{ source.label }}</h3>
        <p>{{ source.source.locator }}</p>
        <blockquote>{{ source.excerpt }}</blockquote>
        <RouterLink v-if="sourceTarget" :to="sourceTarget">打开原文</RouterLink>
      </template>
    </dialog>
  </div>
</template>

<style scoped>
.chat {
  display: grid;
  grid-template-columns: clamp(185px, 18vw, 250px) minmax(0, 1fr);
  height: 100%;
  background: white;
}
.history {
  min-height: 0;
  overflow: auto;
  background: #f5f7fb;
  border-right: 1px solid var(--border);
  padding: 22px 14px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
}
.history-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 650;
  font-size: 15px;
  margin: 0 6px 14px;
}
.new-chat {
  color: var(--primary);
  background: #e7edff;
  justify-content: flex-start;
}
.new-chat:hover {
  background: #dce6ff;
}
.new-chat .icon {
  width: 16px;
}
.history input {
  min-width: 0;
  width: 100%;
  font-size: 13px;
  margin-top: 7px;
}
.history small {
  margin: 20px 7px 4px;
  color: var(--muted);
  font-weight: 600;
}
.history a,
.history p {
  padding: 8px;
  border-radius: 7px;
  font-size: 13px;
}
.history p {
  color: var(--muted);
}
.history a {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.history a:hover,
.history a.router-link-exact-active {
  background: #e8edf7;
  color: #214e92;
}
.thread {
  position: relative;
  min-width: 0;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: min(100%, 870px);
  margin: 0 auto;
  padding: 18px clamp(16px, 4vw, 52px) 24px;
}
.thread.empty {
  justify-content: center;
  overflow: auto;
}
.welcome {
  text-align: center;
  display: grid;
  justify-items: center;
  gap: 11px;
  margin: 0 auto 12px;
}
.welcome h1 {
  font-size: clamp(26px, 3vw, 36px);
  letter-spacing: -0.05em;
}
.welcome p {
  color: var(--muted);
  font-size: 14px;
  line-height: 1.65;
}
.assistant-symbol {
  width: 55px;
  height: 55px;
  display: grid;
  place-items: center;
  border-radius: 17px;
  background: linear-gradient(145deg, #edf2ff, #dee7fe);
  color: var(--primary);
  margin-bottom: 8px;
}
.assistant-symbol .icon {
  width: 33px;
  height: 33px;
  stroke-width: 2;
}
.starters {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}
.starters button {
  padding: 7px 11px;
  display: flex;
  align-items: center;
  gap: 7px;
  color: #455575;
  background: white;
  border: 1px solid var(--border);
  border-radius: 20px;
  font-size: 12px;
}
.starters .icon {
  height: 15px;
  width: 15px;
  color: var(--primary);
}
.message-list {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  scrollbar-gutter: stable;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 25px;
  padding: 8px 4px 16px 0;
}
.message-list article {
  max-width: 100%;
  overflow-wrap: anywhere;
  flex-shrink: 0;
}
.bubble {
  align-self: flex-end;
  padding: 10px 16px;
  border-radius: 16px 16px 5px 16px;
  background: #edf2fe;
  white-space: pre-wrap;
}
.reply {
  width: 100%;
  padding: 0 4px;
  line-height: 1.7;
}
.byline {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 650;
  margin-bottom: 9px;
}
.byline .icon {
  width: 25px;
  height: 25px;
  border-radius: 8px;
  background: #e9eeff;
  padding: 3px;
  color: var(--primary);
}
.reply li {
  margin-block: 4px;
}
.finding {
  border-left: 3px solid #b5c2d7;
  background: #f6f8fb;
  margin-top: 10px;
  padding: 10px 14px;
  border-radius: 0 7px 7px 0;
}
.finding[data-verdict='DOES_NOT_MEET'] {
  border-color: #c06a4e;
}
.finding[data-verdict='MEETS'] {
  border-color: #348776;
}
.source-button {
  color: var(--primary);
  font-size: 12px;
}
.message-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 12px;
}
.message-actions .button,
.code-panel .text-button {
  min-height: 30px;
  font-size: 12px;
  padding: 4px 8px;
}
.code-panel {
  margin-top: 12px;
  border: 1px solid var(--border);
  border-radius: 9px;
  overflow: hidden;
}
.code-panel header {
  background: #f4f6fa;
  padding: 5px 9px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.code-panel header button {
  margin-left: auto;
}
.code-panel pre,
.safe-markdown :deep(pre) {
  margin: 0;
  padding: 12px;
  overflow: auto;
  color: #22354a;
  font:
    13px/1.65 Consolas,
    monospace;
}
.composer {
  background: white;
  padding: 12px 14px;
  border: 1px solid #cdd6e7;
  border-radius: 17px;
  box-shadow: 0 4px 18px #2036540e;
  flex-shrink: 0;
}
.composer:focus-within {
  border-color: #8ca8ef;
  box-shadow: 0 0 0 3px #4f71ce12;
}
.composer textarea {
  display: block;
  width: 100%;
  height: 64px;
  max-height: 18vh;
  resize: vertical;
  padding: 2px 4px;
  border: 0;
  outline: none;
}
.composer-actions,
.files {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.composer-actions .button {
  min-height: 34px;
  border-radius: 9px;
  padding: 5px 12px;
}
.composer-actions .icon {
  width: 16px;
}
.attachment {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px;
  color: var(--muted);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}
.attachment:focus-within {
  outline: 2px solid var(--primary);
}
.attachment input {
  position: absolute;
  inset: 0;
  opacity: 0;
  width: 100%;
  cursor: pointer;
}
.files {
  justify-content: flex-start;
  color: var(--muted);
  font-size: 12px;
}
.state {
  color: var(--muted);
  font-size: 13px;
}
.state[role='alert'] {
  color: var(--danger);
}
.source-drawer {
  position: fixed;
  inset: 0 0 0 auto;
  margin: 0;
  width: min(430px, 100%);
  height: 100dvh;
  max-height: 100dvh;
  padding: 24px;
  border: 0;
  color: var(--ink);
  box-shadow: -8px 0 32px #15274120;
}
.source-drawer::backdrop {
  background: #172b4438;
}
.source-drawer h3 {
  margin: 30px 0 8px;
}
.source-drawer blockquote {
  white-space: pre-wrap;
  line-height: 1.8;
  margin: 18px 0;
  padding: 14px;
  background: var(--canvas);
  border-left: 3px solid var(--primary);
}
.source-drawer a {
  color: var(--primary);
}
.capability {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 12px;
  color: #79531d;
  border-radius: 8px;
  background: #fff6df;
  font-size: 12px;
}
.capability small {
  display: block;
  color: #786645;
}
.capability button {
  white-space: nowrap;
  font-size: 12px;
}
.history-toggle,
.dismiss {
  display: none;
}
@media (max-width: 740px) {
  .chat {
    display: block;
    position: relative;
  }
  .history {
    display: none;
  }
  .history.is-open {
    position: absolute;
    display: flex;
    inset: 0 auto 0 0;
    width: min(295px, 95%);
    z-index: 3;
    box-shadow: 12px 0 32px #17273e22;
  }
  .dismiss,
  .history-toggle {
    display: flex;
  }
  .history-toggle {
    align-self: flex-start;
    font-size: 12px;
    min-height: 32px;
  }
  .thread.empty .history-toggle {
    position: absolute;
    top: 10px;
    left: 14px;
  }
  .thread {
    padding: 10px 14px 12px;
    gap: 8px;
  }
  .starters {
    gap: 4px;
  }
  .starters button {
    font-size: 11px;
    padding: 6px 7px;
  }
  .welcome p {
    max-width: 29em;
  }
  .thread.empty {
    padding-top: 45px;
  }
}
@media (max-height: 520px) {
  .thread.empty {
    justify-content: flex-start;
  }
  .welcome {
    gap: 3px;
  }
  .assistant-symbol {
    display: none;
  }
}
</style>
