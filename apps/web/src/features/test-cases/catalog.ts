import { reactive, readonly } from 'vue';
import { ApiError, errorMessage, requestId } from '../../app/http';
import { readContract } from '../../app/contracts';
import {
  catalogApi,
  type CaseFilter,
  type CaseSort,
  type Selection,
  type SelectionCreate,
  type Suite,
  type TestCase,
} from './api';

type Phase = 'idle' | 'loading' | 'ready' | 'error';
type CatalogApi = Pick<typeof catalogApi, 'listSuites' | 'listTestCases' | 'createCaseSelection'>;
export function createCaseCatalog(api: CatalogApi = catalogApi) {
  const state = reactive({
    projectId: '',
    suites: [] as Suite[],
    suitePhase: 'idle' as Phase,
    suiteError: '',
    items: [] as TestCase[],
    phase: 'idle' as Phase,
    error: '',
    requestId: '',
    filter: { lifecycleStatus: 'ACTIVE' } as CaseFilter,
    page: 0,
    size: 20,
    total: 0,
    sort: 'title,asc' as CaseSort,
    explicitIds: [] as string[],
    excludedIds: [] as string[],
    allMatching: false,
    selection: null as Selection | null,
    selectionError: '',
    selectionRequestId: '',
    saving: false,
  });
  let listVersion = 0,
    suiteVersion = 0,
    choiceVersion = 0;
  let listController: AbortController | undefined,
    suiteController: AbortController | undefined,
    choiceController: AbortController | undefined;
  let pendingKey = '';
  function clearSelection() {
    choiceVersion++;
    choiceController?.abort();
    pendingKey = '';
    Object.assign(state, {
      explicitIds: [],
      excludedIds: [],
      allMatching: false,
      selection: null,
      selectionError: '',
      selectionRequestId: '',
      saving: false,
    });
  }
  function cancel() {
    listVersion++;
    suiteVersion++;
    listController?.abort();
    suiteController?.abort();
  }
  function setProject(id: string) {
    if (state.projectId === id) return;
    cancel();
    clearSelection();
    Object.assign(state, {
      projectId: id,
      suites: [],
      suitePhase: 'idle',
      suiteError: '',
      items: [],
      phase: 'idle',
      error: '',
      requestId: '',
      filter: { lifecycleStatus: 'ACTIVE' },
      page: 0,
      size: 20,
      total: 0,
      sort: 'title,asc',
    });
  }
  async function loadSuites() {
    const projectId = state.projectId;
    if (!projectId) return;
    suiteController?.abort();
    suiteController = new AbortController();
    const signal = suiteController.signal,
      version = ++suiteVersion;
    state.suites = [];
    state.suitePhase = 'loading';
    state.suiteError = '';
    try {
      const items: Suite[] = [];
      for (let page = 0; ; page++) {
        const result = await api.listSuites(projectId, page, signal);
        if (signal.aborted || version !== suiteVersion) return;
        if (!result.items.length && items.length < result.total)
          throw new Error('套件列表不完整，请重试。');
        items.push(...result.items);
        if (items.length >= result.total) break;
      }
      const byId = new Map(items.map((item) => [item.id, item]));
      if (byId.size !== items.length) throw new Error('套件列表存在重复项，请重试。');
      for (const item of items) {
        const visited = new Set<string>();
        let current: Suite | undefined = item;
        while (current) {
          if (visited.has(current.id)) throw new Error('套件层级异常，请联系管理员。');
          visited.add(current.id);
          current = current.parentId ? byId.get(current.parentId) : undefined;
        }
      }
      state.suites = items;
      state.suitePhase = 'ready';
    } catch (error) {
      if (signal.aborted || version !== suiteVersion) return;
      state.suites = [];
      state.suitePhase = 'error';
      state.suiteError = errorMessage(error);
      if (error instanceof ApiError && (error.status === 403 || error.status === 404)) {
        cancel();
        clearSelection();
        state.items = [];
        state.total = 0;
        state.phase = 'error';
        state.error = errorMessage(error);
      }
    }
  }
  async function loadCases(page = state.page) {
    const projectId = state.projectId;
    if (!projectId) return;
    listController?.abort();
    listController = new AbortController();
    const signal = listController.signal,
      version = ++listVersion;
    state.page = page;
    state.items = [];
    state.phase = 'loading';
    state.error = '';
    state.requestId = '';
    try {
      const result = await api.listTestCases(
        projectId,
        { ...state.filter, tags: state.filter.tags ? [...state.filter.tags] : undefined },
        page,
        state.size,
        state.sort,
        signal,
      );
      if (signal.aborted || version !== listVersion) return;
      if (page > 0 && result.items.length === 0) {
        await loadCases(Math.max(0, Math.ceil(result.total / state.size) - 1));
        return;
      }
      state.items = result.items;
      state.total = result.total;
      state.phase = 'ready';
    } catch (error) {
      if (signal.aborted || version !== listVersion) return;
      state.items = [];
      state.total = 0;
      state.phase = 'error';
      state.error = errorMessage(error);
      state.requestId = requestId(error);
      if (error instanceof ApiError && (error.status === 403 || error.status === 404)) {
        suiteVersion++;
        suiteController?.abort();
        state.suites = [];
        clearSelection();
      }
    }
  }
  async function applyFilter(filter: CaseFilter, sort = state.sort, size = state.size) {
    readContract('CaseFilter', filter);
    clearSelection();
    state.filter = filter;
    state.sort = sort;
    state.size = size;
    await loadCases(0);
  }
  function isSelected(id: string) {
    return state.allMatching ? !state.excludedIds.includes(id) : state.explicitIds.includes(id);
  }
  function changeChoice() {
    choiceVersion++;
    choiceController?.abort();
    state.selection = null;
    state.selectionError = '';
    state.selectionRequestId = '';
    state.saving = false;
    pendingKey = '';
  }
  function toggleCase(id: string) {
    changeChoice();
    const ids = state.allMatching ? state.excludedIds : state.explicitIds;
    const index = ids.indexOf(id);
    if (index >= 0) ids.splice(index, 1);
    else if (ids.length < 1000) ids.push(id);
    else state.selectionError = '选择或排除最多 1000 条，请缩小筛选范围。';
  }
  function selectPage() {
    const all = state.items.length > 0 && state.items.every((item) => isSelected(item.id));
    for (const item of state.items) if (isSelected(item.id) === all) toggleCase(item.id);
  }
  function selectAllMatching() {
    clearSelection();
    state.allMatching = true;
  }
  async function confirmSelection() {
    if (
      state.saving ||
      state.phase !== 'ready' ||
      (!state.allMatching && !state.explicitIds.length)
    )
      return;
    const version = choiceVersion,
      projectId = state.projectId;
    const body: SelectionCreate = state.allMatching
      ? { mode: 'FILTER', filter: { ...state.filter }, excludedCaseIds: [...state.excludedIds] }
      : { mode: 'EXPLICIT', caseIds: [...state.explicitIds] };
    pendingKey ||= crypto.randomUUID();
    choiceController = new AbortController();
    const signal = choiceController.signal;
    state.saving = true;
    state.selectionError = '';
    state.selectionRequestId = '';
    try {
      const result = await api.createCaseSelection(projectId, body, pendingKey, signal);
      if (signal.aborted || version !== choiceVersion) return;
      state.selection = result;
    } catch (error) {
      if (signal.aborted || version !== choiceVersion) return;
      state.selection = null;
      state.selectionError = errorMessage(error);
      state.selectionRequestId = requestId(error);
      if (error instanceof ApiError && (error.status === 403 || error.status === 404)) {
        state.items = [];
        state.suites = [];
        state.total = 0;
        state.phase = 'error';
        state.error = errorMessage(error);
        clearSelection();
      }
    } finally {
      if (version === choiceVersion) state.saving = false;
    }
  }
  function renewSelection() {
    state.selection = null;
    pendingKey = '';
    return confirmSelection();
  }
  return {
    state: readonly(state),
    setProject,
    cancel,
    loadSuites,
    loadCases,
    applyFilter,
    clearSelection,
    toggleCase,
    selectPage,
    selectAllMatching,
    isSelected,
    confirmSelection,
    renewSelection,
  };
}
export const caseCatalog = createCaseCatalog();
