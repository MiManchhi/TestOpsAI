import { computed, reactive, readonly } from 'vue';
import { errorMessage, requestId } from '../../app/http';
import { listProjects, type Project } from './api';

export function createProjectContext(loadPage = listProjects) {
  const state = reactive({
    items: [] as Project[],
    selectedId: '',
    phase: 'idle' as 'idle' | 'loading' | 'ready' | 'error',
    error: '',
    requestId: '',
  });
  let version = 0;
  let controller: AbortController | undefined;
  const current = computed(
    () => state.items.find((project) => project.id === state.selectedId) ?? null,
  );
  function select(id: string) {
    if (state.items.some((project) => project.id === id)) state.selectedId = id;
  }
  function updateCurrent(project: Project) {
    if (state.selectedId === project.id)
      state.items = state.items.map((item) => (item.id === project.id ? project : item));
  }
  function reset() {
    version++;
    controller?.abort();
    Object.assign(state, { items: [], selectedId: '', phase: 'idle', error: '', requestId: '' });
  }
  async function load(preferred: unknown = state.selectedId) {
    reset();
    const generation = version;
    controller = new AbortController();
    const signal = controller.signal;
    state.phase = 'loading';
    try {
      const items: Project[] = [];
      let page = 0;
      while (true) {
        const result = await loadPage(page, signal);
        if (signal.aborted || version !== generation) return;
        if (result.page !== page || (items.length < result.total && result.items.length === 0))
          throw new Error('项目列表响应不完整，请重试。');
        items.push(...result.items);
        if (items.length >= result.total) break;
        page++;
      }
      if (new Set(items.map((project) => project.id)).size !== items.length)
        throw new Error('项目列表响应异常，请重试。');
      state.items = items;
      if (
        preferred !== undefined &&
        preferred !== null &&
        preferred !== '' &&
        (typeof preferred !== 'string' || !items.some((project) => project.id === preferred))
      ) {
        state.error = '原项目已不可访问，请选择其他项目。';
      }
      state.selectedId = state.error
        ? ''
        : (items.find((project) => project.id === preferred)?.id ?? items[0]?.id ?? '');
      state.phase = 'ready';
    } catch (error) {
      if (signal.aborted || version !== generation) return;
      state.items = [];
      state.selectedId = '';
      state.phase = 'error';
      state.error = errorMessage(error);
      state.requestId = requestId(error);
    }
  }
  return { state: readonly(state), current, select, updateCurrent, load, reset };
}

export const projects = createProjectContext();
