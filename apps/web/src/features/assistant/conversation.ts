import { reactive } from 'vue';
import type {
  Answer,
  Conversation,
  Message,
  MessageContext,
  Turn,
  TurnEvent,
} from '../../app/contracts';
import { ApiError, errorMessage, requestId } from '../../app/http';
import * as api from './api';
import { streamTurn } from './stream';

type ToolView = {
  id: string;
  label: string;
  status: 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  summary: string;
};
const labels = {
  search_test_cases: '查询用例',
  get_test_case_detail: '读取用例定义',
  get_run_summary: '读取运行摘要',
  get_failed_cases: '查询失败结果',
  search_knowledge: '检索授权资料',
  create_case_draft: '整理建议草稿',
};
const active = (turn: Turn | null) => turn !== null && ['QUEUED', 'RUNNING'].includes(turn.status);
function delay(ms: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    if (signal.aborted) {
      reject(new DOMException('Cancelled', 'AbortError'));
      return;
    }
    const abort = () => {
      clearTimeout(timer);
      reject(new DOMException('Cancelled', 'AbortError'));
    };
    const timer = setTimeout(() => {
      signal.removeEventListener('abort', abort);
      resolve();
    }, ms);
    signal.addEventListener('abort', abort, { once: true });
  });
}

export function createConversationState(deps = { ...api, streamTurn }) {
  const state = reactive({
    projectId: '',
    conversation: null as Conversation | null,
    messages: [] as Message[],
    total: 0,
    page: 0,
    loading: false,
    sending: false,
    cancelling: false,
    connected: false,
    reconnecting: false,
    turn: null as Turn | null,
    sequence: 0,
    delta: '',
    answer: null as Answer | null,
    stage: '',
    tools: [] as ToolView[],
    citationIds: [] as string[],
    context: null as MessageContext | null,
    error: '',
    trace: '',
    notice: '',
  });
  let version = 0,
    historyVersion = 0;
  let loadController: AbortController | undefined,
    historyController: AbortController | undefined,
    connection: AbortController | undefined,
    sendController: AbortController | undefined,
    cancelController: AbortController | undefined;
  let intent = { fingerprint: '', key: '', clientMessageId: '' };
  let cancelIntent = { turnId: '', key: '' };
  function reset(projectId = state.projectId) {
    version++;
    historyVersion++;
    [loadController, historyController, connection, sendController, cancelController].forEach(
      (controller) => controller?.abort(),
    );
    Object.assign(state, {
      projectId,
      conversation: null,
      messages: [],
      total: 0,
      page: 0,
      loading: false,
      sending: false,
      cancelling: false,
      connected: false,
      reconnecting: false,
      turn: null,
      sequence: 0,
      delta: '',
      answer: null,
      stage: '',
      tools: [],
      citationIds: [],
      context: null,
      error: '',
      trace: '',
      notice: '',
    });
    intent = { fingerprint: '', key: '', clientMessageId: '' };
    cancelIntent = { turnId: '', key: '' };
  }
  function fail(cause: unknown) {
    state.error = errorMessage(cause);
    state.trace = requestId(cause);
  }
  function revoke(cause: unknown) {
    reset();
    fail(cause);
  }
  async function reloadMessages(page = state.page) {
    const id = state.conversation?.id;
    if (!id) return;
    const generation = version,
      ticket = ++historyVersion;
    historyController?.abort();
    historyController = new AbortController();
    const signal = historyController.signal;
    try {
      const result = await deps.listMessages(id, page, signal);
      if (signal.aborted || generation !== version || ticket !== historyVersion) return;
      state.messages = result.items;
      state.total = result.total;
      state.page = page;
      const message = state.messages.find(
        (item) => item.turnId === state.turn?.id && item.role === 'ASSISTANT' && item.answer,
      );
      if (message?.answer && state.turn?.status === 'COMPLETED') state.answer = message.answer;
    } catch (cause) {
      if (signal.aborted || generation !== version) return;
      if (cause instanceof ApiError && [403, 404].includes(cause.status)) revoke(cause);
      else fail(cause);
    }
  }
  async function restoreFinal(signal: AbortSignal, generation: number) {
    if (state.turn?.status !== 'COMPLETED' || state.answer) return;
    const turnId = state.turn.id,
      conversationId = state.conversation?.id;
    if (!conversationId) return;
    const pages = Math.min(100, Math.ceil(state.total / 20));
    for (let page = 0; page < pages; page++) {
      const messages =
        page === state.page
          ? state.messages
          : (await deps.listMessages(conversationId, page, signal)).items;
      if (signal.aborted || generation !== version) return;
      const final = messages.find(
        (message) => message.role === 'ASSISTANT' && message.turnId === turnId && message.answer,
      );
      if (final?.answer) {
        state.answer = final.answer;
        state.context =
          final.context ??
          messages.find((message) => message.role === 'USER' && message.turnId === turnId)
            ?.context ??
          null;
        return;
      }
    }
    state.notice = '本轮已完成，最终消息尚未读取，请刷新或翻阅会话记录。';
  }
  function apply(event: TurnEvent) {
    if (!state.turn || !state.conversation) throw new Error('回答上下文已失效。');
    if (event.event === 'turn.started' && event.data.conversationId !== state.conversation.id)
      throw new Error('回答会话范围不匹配。');
    state.sequence = event.sequence;
    if (event.event === 'turn.started')
      state.turn = { ...event.data, lastSequence: event.sequence };
    else if (event.event === 'stage.changed') state.stage = event.data.label;
    else if (event.event === 'answer.delta') {
      if (state.delta.length + event.data.text.length > 50_000)
        throw new Error('增量回答超过显示限制，请读取最终记录。');
      state.delta += event.data.text;
    } else if (event.event === 'tool.started') {
      if (state.tools.length >= 100) throw new Error('本轮查询步骤超过显示限制。');
      state.tools.push({
        id: event.data.toolCallId,
        label: labels[event.data.tool],
        status: 'RUNNING',
        summary: '',
      });
    } else if (event.event === 'tool.finished') {
      const tool = state.tools.find((item) => item.id === event.data.toolCallId);
      if (tool) {
        tool.status = event.data.status;
        tool.summary = event.data.summary;
      }
    } else if (event.event === 'citation.available') {
      if (!state.citationIds.includes(event.data.id)) state.citationIds.push(event.data.id);
    } else if (event.event === 'turn.completed') {
      state.answer = event.data.answer;
      state.turn = { ...state.turn, status: 'COMPLETED', lastSequence: event.sequence };
      state.delta = '';
      state.notice = '回答已完成，业务建议需检查后确认保存。';
    } else if (event.event === 'turn.failed') {
      state.turn = { ...state.turn, status: 'FAILED', lastSequence: event.sequence };
      state.error = event.data.message;
    } else if (event.event === 'turn.cancelled') {
      if (event.data.reason === 'AUTH_REVOKED') {
        revoke(new ApiError('访问权限已变化，已隐藏会话内容。', 403));
        return;
      }
      state.turn = { ...state.turn, status: 'CANCELLED', lastSequence: event.sequence };
      state.notice =
        event.data.reason === 'USER_REQUEST'
          ? '本轮回答已停止。'
          : '连接中断后本轮已结束，请读取会话记录。';
    }
  }
  async function reconnect() {
    if (!state.turn || !state.conversation) return;
    if (!active(state.turn)) {
      await reloadMessages();
      return;
    }
    connection?.abort();
    connection = new AbortController();
    const controller = connection,
      generation = version,
      turnId = state.turn.id,
      conversationId = state.conversation.id;
    const deadline = Date.now() + 120_000;
    let attempts = 0,
      disconnectedAt = 0;
    state.error = state.trace = '';
    state.connected = true;
    try {
      while (!controller.signal.aborted && generation === version) {
        try {
          const signal = AbortSignal.any([
            controller.signal,
            AbortSignal.timeout(Math.max(1, deadline - Date.now())),
          ]);
          await deps.streamTurn(turnId, state.sequence, signal, (event) => {
            if (!controller.signal.aborted && generation === version) {
              if (state.reconnecting) state.notice = '';
              state.reconnecting = false;
              apply(event);
            }
          });
          if (generation === version && !controller.signal.aborted) await reloadMessages();
          return;
        } catch (cause) {
          if (controller.signal.aborted || generation !== version) return;
          if (cause instanceof ApiError && [401, 403, 404].includes(cause.status)) {
            revoke(cause);
            return;
          }
          if (cause instanceof ApiError && cause.code === 'EVENT_REPLAY_EXPIRED') {
            const turn = await deps.getTurn(conversationId, turnId, controller.signal);
            if (generation !== version || controller.signal.aborted) return;
            state.turn = turn;
            await reloadMessages();
            await restoreFinal(controller.signal, generation);
            state.notice = '事件重放已过期，已读取持久化记录；未重复发送问题。';
            return;
          }
          if (!disconnectedAt) disconnectedAt = Date.now();
          if (
            attempts++ >= 2 ||
            Date.now() >= deadline ||
            Date.now() - disconnectedAt > 25_000 ||
            (cause instanceof ApiError && cause.status >= 400)
          ) {
            fail(cause);
            return;
          }
          state.reconnecting = true;
          state.notice = '连接中断，正在按事件序号恢复…';
          await delay(500 * attempts, controller.signal);
        }
      }
    } catch (cause) {
      if (!controller.signal.aborted && generation === version) fail(cause);
    } finally {
      if (generation === version && connection === controller) {
        state.connected = false;
        state.reconnecting = false;
      }
    }
  }
  async function load(id: string) {
    reset();
    if (!id || !state.projectId) return;
    const generation = version;
    loadController = new AbortController();
    const signal = loadController.signal;
    state.loading = true;
    try {
      const conversation = await deps.getConversation(state.projectId, id, signal);
      if (signal.aborted || generation !== version) return;
      state.conversation = conversation;
      await reloadMessages(0);
      if (signal.aborted || generation !== version) return;
      const candidate = state.messages.filter((item) => item.role === 'USER' && item.turnId).at(-1);
      if (
        candidate?.turnId &&
        !state.messages.some(
          (item) => item.role === 'ASSISTANT' && item.turnId === candidate.turnId,
        )
      ) {
        const turn = await deps.getTurn(id, candidate.turnId, signal);
        if (signal.aborted || generation !== version) return;
        state.turn = turn;
        if (active(turn)) void reconnect();
        else await restoreFinal(signal, generation);
      }
    } catch (cause) {
      if (!signal.aborted && generation === version) fail(cause);
    } finally {
      if (generation === version) state.loading = false;
    }
  }
  async function send(text: string, context: MessageContext) {
    if (!state.conversation || state.sending || state.loading || active(state.turn)) return false;
    const generation = version,
      id = state.conversation.id;
    const fingerprint = JSON.stringify([id, text, context]);
    if (intent.fingerprint !== fingerprint)
      intent = { fingerprint, key: crypto.randomUUID(), clientMessageId: crypto.randomUUID() };
    sendController = new AbortController();
    const signal = sendController.signal;
    state.sending = true;
    state.error = state.trace = state.notice = '';
    try {
      const turn = await deps.sendMessage(
        id,
        { clientMessageId: intent.clientMessageId, text, context },
        intent.key,
        signal,
      );
      if (signal.aborted || generation !== version) return false;
      state.turn = turn;
      state.sequence = 0;
      state.delta = '';
      state.answer = null;
      state.tools = [];
      state.citationIds = [];
      state.stage = '';
      state.context = context;
      intent = { fingerprint: '', key: '', clientMessageId: '' };
      await reloadMessages();
      if (signal.aborted || generation !== version) return false;
      if (active(turn)) void reconnect();
      else await restoreFinal(signal, generation);
      return true;
    } catch (cause) {
      if (!signal.aborted && generation === version) {
        if (cause instanceof ApiError && [403, 404].includes(cause.status)) revoke(cause);
        else fail(cause);
      }
      return false;
    } finally {
      if (generation === version) state.sending = false;
    }
  }
  async function stop() {
    if (!state.turn || !state.conversation || state.cancelling || !active(state.turn)) return;
    const generation = version,
      id = state.turn.id;
    if (cancelIntent.turnId !== id) cancelIntent = { turnId: id, key: crypto.randomUUID() };
    cancelController = new AbortController();
    const signal = cancelController.signal;
    state.cancelling = true;
    try {
      const result = await deps.cancelTurn(state.conversation.id, id, cancelIntent.key, signal);
      if (signal.aborted || generation !== version) return;
      if (!active(state.turn) && state.turn?.status !== result.status)
        throw new Error('回答终态发生冲突，请重新读取记录。');
      state.turn = result;
      if (!active(result)) {
        connection?.abort();
        state.connected = state.reconnecting = false;
        await reloadMessages();
        await restoreFinal(signal, generation);
        state.notice =
          result.status === 'COMPLETED'
            ? state.answer
              ? '回答已先完成，已读取最终记录。'
              : '回答已完成，请刷新或翻阅会话记录。'
            : '本轮回答已停止。';
      }
    } catch (cause) {
      if (!signal.aborted && generation === version) {
        if (cause instanceof ApiError && [403, 404].includes(cause.status)) revoke(cause);
        else fail(cause);
      }
    } finally {
      if (generation === version) state.cancelling = false;
    }
  }
  return {
    state,
    reset,
    load,
    send,
    stop,
    reconnect,
    reloadMessages,
    get active() {
      return active(state.turn);
    },
  };
}
