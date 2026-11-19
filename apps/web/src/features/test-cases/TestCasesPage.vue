<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import AppIcon from '../../app/AppIcon.vue';
import { errorMessage } from '../../app/http';
import { projects } from '../projects/context';
import { caseCatalog as catalog } from './catalog';
import type { CaseFilter, CaseSort } from './api';
import SuiteBranch from './SuiteBranch.vue';

const state = catalog.state;
const draft = reactive({
  query: '',
  module: '',
  tags: '',
  tagsMatch: 'ALL' as 'ALL' | 'ANY',
  priority: '' as '' | NonNullable<CaseFilter['priority']>,
  lifecycleStatus: 'ACTIVE' as '' | NonNullable<CaseFilter['lifecycleStatus']>,
  sourceType: '' as '' | NonNullable<CaseFilter['sourceType']>,
  includeDescendants: true,
  sort: 'title,asc' as CaseSort,
  size: 20,
});
const filterError = ref('');
const now = ref(Date.now());
let clock: ReturnType<typeof setInterval> | undefined;
onMounted(() => {
  clock = setInterval(() => {
    now.value = Date.now();
  }, 30_000);
});
onBeforeUnmount(() => {
  clearInterval(clock);
  catalog.cancel();
});
const roots = computed(() =>
  state.suites.filter(
    (suite) => !suite.parentId || !state.suites.some((parent) => parent.id === suite.parentId),
  ),
);
const pageSelected = computed(
  () => state.items.length > 0 && state.items.every((item) => catalog.isSelected(item.id)),
);
const expired = computed(
  () => state.selection !== null && Date.parse(state.selection.expiresAt) <= now.value,
);
const selectionLabel = computed(() =>
  state.allMatching
    ? `全部匹配结果${state.excludedIds.length ? `，排除 ${state.excludedIds.length} 条` : ''}`
    : `${state.explicitIds.length} 条用例`,
);
function syncDraft() {
  const filter = state.filter;
  Object.assign(draft, {
    query: filter.query ?? '',
    module: filter.module ?? '',
    tags: filter.tags?.join(', ') ?? '',
    tagsMatch: filter.tagsMatch ?? 'ALL',
    priority: filter.priority ?? '',
    lifecycleStatus: filter.lifecycleStatus ?? '',
    sourceType: filter.sourceType ?? '',
    includeDescendants: filter.includeDescendants ?? true,
    sort: state.sort,
    size: state.size,
  });
}
watch(() => state.filter, syncDraft, { immediate: true });
watch(
  () => projects.state.selectedId,
  (id) => {
    catalog.setProject(id);
    if (id) {
      void catalog.loadSuites();
      void catalog.loadCases();
    }
  },
  { immediate: true },
);
async function apply() {
  filterError.value = '';
  const tags = [
    ...new Set(
      draft.tags
        .split(/[,，\n]/)
        .map((tag) => tag.trim())
        .filter(Boolean),
    ),
  ];
  const filter: CaseFilter = {
    ...(state.filter.suiteId
      ? { suiteId: state.filter.suiteId, includeDescendants: draft.includeDescendants }
      : {}),
    ...(draft.query.trim() ? { query: draft.query.trim() } : {}),
    ...(draft.module.trim() ? { module: draft.module.trim() } : {}),
    ...(tags.length ? { tags, tagsMatch: draft.tagsMatch } : {}),
    ...(draft.priority ? { priority: draft.priority } : {}),
    ...(draft.lifecycleStatus ? { lifecycleStatus: draft.lifecycleStatus } : {}),
    ...(draft.sourceType ? { sourceType: draft.sourceType } : {}),
  };
  try {
    await catalog.applyFilter(filter, draft.sort, draft.size);
  } catch (error) {
    filterError.value = errorMessage(error);
  }
}
function chooseSuite(id?: string) {
  void catalog.applyFilter({
    ...state.filter,
    tags: state.filter.tags ? [...state.filter.tags] : undefined,
    suiteId: id,
    includeDescendants: true,
  });
}
function reset() {
  filterError.value = '';
  void catalog.applyFilter({ lifecycleStatus: 'ACTIVE' }, 'title,asc', 20);
}
function date(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(value),
  );
}
</script>
<template>
  <div class="catalog-page">
    <div class="page-heading-row">
      <div>
        <p class="section-kicker">METER TESTING / 测试协作</p>
        <h1 data-page-heading tabindex="-1">Robot 用例</h1>
        <p class="muted">按套件与标签精确筛选，确认同一组测试范围。</p>
      </div>
      <button
        class="button button-secondary"
        :disabled="state.phase === 'loading'"
        @click="catalog.loadCases()"
      >
        <AppIcon name="refresh" />刷新列表
      </button>
    </div>
    <div class="project-context-strip">
      <span class="small-dot" /><strong>{{ projects.current.value?.name }}</strong
      ><span>定义与来源可追溯</span
      ><span class="context-end">{{ projects.current.value?.key }}</span>
    </div>
    <div class="catalog-grid">
      <aside class="suite-panel" aria-labelledby="suite-heading">
        <div class="catalog-panel-heading">
          <h2 id="suite-heading">测试套件</h2>
          <span class="muted small-text">{{ state.suites.length }} 个</span>
        </div>
        <p v-if="state.suitePhase === 'loading'" class="inline-state" role="status">
          正在读取套件…
        </p>
        <div v-else-if="state.suitePhase === 'error'" class="inline-state">
          <p role="alert">{{ state.suiteError }}</p>
          <button class="text-button" @click="catalog.loadSuites()">重新读取套件</button>
        </div>
        <template v-else
          ><button
            class="suite-all"
            :class="{ selected: !state.filter.suiteId }"
            :aria-pressed="!state.filter.suiteId"
            @click="chooseSuite()"
          >
            <AppIcon name="grid" />全部套件
          </button>
          <ul class="suite-tree">
            <SuiteBranch
              v-for="suite in roots"
              :key="suite.id"
              :suite="suite"
              :suites="state.suites"
              :selected-id="state.filter.suiteId"
              @select="chooseSuite"
            />
          </ul>
          <p v-if="!roots.length" class="inline-state muted">
            暂无套件，可查看人工用例。
          </p></template
        >
      </aside>
      <div class="catalog-main">
        <form class="case-filter" aria-label="用例筛选" @submit.prevent="apply">
          <div class="filter-primary">
            <label class="search-field"
              >搜索用例<input
                v-model="draft.query"
                type="search"
                maxlength="256"
                placeholder="搜索名称或说明" /></label
            ><label
              >精确标签<input
                v-model="draft.tags"
                maxlength="2000"
                placeholder="如 smoke, tariff"
                aria-describedby="tag-help" /></label
            ><button class="button button-primary" type="submit">查询</button
            ><button class="text-button" type="button" @click="reset">重置</button>
          </div>
          <p id="tag-help" class="field-hint">多个标签以逗号分隔，区分大小写并完整匹配。</p>
          <details class="filter-more">
            <summary>更多筛选与排序</summary>
            <div class="filter-grid">
              <label
                >所属模块<input
                  v-model="draft.module"
                  maxlength="128"
                  placeholder="精确匹配模块" /></label
              ><label
                >标签关系<select v-model="draft.tagsMatch">
                  <option value="ALL">同时包含全部标签</option>
                  <option value="ANY">包含任一标签</option>
                </select></label
              >
              <label
                >优先级<select v-model="draft.priority">
                  <option value="">全部优先级</option>
                  <option
                    v-for="priority in ['P0', 'P1', 'P2', 'P3']"
                    :key="priority"
                    :value="priority"
                  >
                    {{ priority }}
                  </option>
                </select></label
              >
              <label
                >定义状态<select v-model="draft.lifecycleStatus">
                  <option value="">全部状态</option>
                  <option value="ACTIVE">有效</option>
                  <option value="ARCHIVED">已归档</option>
                </select></label
              >
              <label
                >用例来源<select v-model="draft.sourceType">
                  <option value="">全部来源</option>
                  <option value="ROBOT">Robot 定义</option>
                  <option value="MANUAL">人工用例</option>
                </select></label
              >
              <label
                >排序<select v-model="draft.sort">
                  <option value="title,asc">名称升序</option>
                  <option value="title,desc">名称降序</option>
                  <option value="updatedAt,desc">最近更新</option>
                  <option value="updatedAt,asc">最早更新</option>
                </select></label
              >
              <label
                >每页条数<select v-model.number="draft.size">
                  <option :value="20">20 条</option>
                  <option :value="50">50 条</option>
                  <option :value="100">100 条</option>
                </select></label
              >
              <label v-if="state.filter.suiteId" class="checkbox-label"
                ><input v-model="draft.includeDescendants" type="checkbox" />包含子套件</label
              >
            </div>
          </details>
          <p v-if="filterError" class="form-error" role="alert">{{ filterError }}</p>
        </form>
        <div class="selection-bar" aria-live="polite">
          <div>
            <strong>已选择 {{ selectionLabel }}</strong
            ><small>跨页保留选择，确认后固定用例版本。</small>
          </div>
          <div class="button-row">
            <button
              class="text-button"
              :disabled="!state.allMatching && !state.explicitIds.length"
              @click="catalog.clearSelection"
            >
              清空</button
            ><button
              class="button button-primary"
              :disabled="
                state.saving ||
                state.phase !== 'ready' ||
                (!state.allMatching && !state.explicitIds.length)
              "
              @click="catalog.confirmSelection()"
            >
              {{ state.saving ? '正在确认…' : '确认选择' }}
            </button>
          </div>
        </div>
        <div v-if="state.selectionError" class="catalog-alert" role="alert">
          <p>{{ state.selectionError }}</p>
          <small v-if="state.selectionRequestId">请求编号：{{ state.selectionRequestId }}</small>
        </div>
        <div v-if="state.selection" class="selection-confirmed" role="status">
          <strong>{{
            expired ? '选择快照已过期，请重新确认' : `已确认 ${state.selection.count} 条用例`
          }}</strong>
          <p>有效期至 {{ date(state.selection.expiresAt) }}。用例及版本已由服务端固定。</p>
          <RouterLink
            v-if="!expired && state.selection.count > 0"
            class="button button-primary"
            :to="{
              path: '/assistant',
              query: { projectId: projects.state.selectedId, selectionId: state.selection.id },
            }"
            >问所选用例 →</RouterLink
          >
          <button v-if="expired" class="text-button" @click="catalog.renewSelection()">
            重新确认
          </button>
          <details>
            <summary>查看选择摘要</summary>
            <code>{{ state.selection.digest }}</code>
          </details>
        </div>
        <section
          class="case-table-panel"
          aria-label="用例列表"
          :aria-busy="state.phase === 'loading'"
        >
          <div class="table-toolbar">
            <span>{{
              state.phase === 'ready' ? `共 ${state.total} 条匹配用例` : '用例列表'
            }}</span><button
              class="text-button"
              :aria-pressed="state.allMatching"
              :disabled="state.phase !== 'ready' || !state.total"
              @click="catalog.selectAllMatching"
            >
              选择全部匹配结果
            </button>
          </div>
          <div v-if="state.phase === 'loading'" class="catalog-empty" role="status">
            <span class="spinner" />正在读取用例…
          </div>
          <div v-else-if="state.phase === 'error'" class="catalog-empty">
            <AppIcon name="shield" />
            <h2>无法读取用例</h2>
            <p role="alert">{{ state.error }}</p>
            <small v-if="state.requestId">请求编号：{{ state.requestId }}</small
            ><button class="button button-secondary" @click="catalog.loadCases()">重新读取</button>
          </div>
          <div v-else-if="!state.items.length" class="catalog-empty">
            <AppIcon name="check" />
            <h2>没有匹配用例</h2>
            <p>调整套件、关键词或精确标签后再查询。</p>
            <button class="button button-secondary" @click="reset">清空筛选条件</button>
          </div>
          <div
            v-else
            class="case-table-scroll"
            role="region"
            aria-label="可横向滚动的用例表格"
            tabindex="0"
          >
            <table class="case-table">
              <thead>
                <tr>
                  <th class="selection-column">
                    <input
                      type="checkbox"
                      aria-label="选择本页用例"
                      :checked="pageSelected"
                      :indeterminate="
                        !pageSelected && state.items.some((item) => catalog.isSelected(item.id))
                      "
                      @change="catalog.selectPage"
                    />
                  </th>
                  <th>用例名称</th>
                  <th>精确标签</th>
                  <th>优先级</th>
                  <th>来源 / 定义状态</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="item in state.items"
                  :key="item.id"
                  :class="{ 'is-selected': catalog.isSelected(item.id) }"
                >
                  <td>
                    <input
                      type="checkbox"
                      :aria-label="`选择${item.title}`"
                      :checked="catalog.isSelected(item.id)"
                      @change="catalog.toggleCase(item.id)"
                    />
                  </td>
                  <td>
                    <RouterLink
                      class="case-title"
                      :to="{
                        path: `/test-cases/${item.id}`,
                        query: { projectId: state.projectId },
                      }"
                      >{{ item.title }}</RouterLink><span class="case-subtitle"
                      >{{ item.module || '未分类' }} · v{{ item.version }}</span
                    >
                  </td>
                  <td>
                    <div class="tag-list">
                      <span v-for="tag in item.tags" :key="tag" class="case-tag">{{
                        tag
                      }}</span><span v-if="!item.tags.length" class="muted">—</span>
                    </div>
                  </td>
                  <td>
                    <span
                      class="priority-badge"
                      :class="{ 'priority-critical': item.priority === 'P0' }"
                      >{{ item.priority }}</span
                    >
                  </td>
                  <td>
                    <span>{{ item.sourceType === 'ROBOT' ? 'Robot 定义' : '人工用例' }}</span><span
                      class="case-subtitle"
                      >{{ item.lifecycleStatus === 'ACTIVE' ? '有效' : '已归档' }}</span
                    >
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <nav
            v-if="state.phase === 'ready' && state.total > 0"
            class="case-pagination"
            aria-label="用例分页"
          >
            <span>第 {{ state.page + 1 }} / {{ Math.ceil(state.total / state.size) }} 页</span>
            <div class="button-row">
              <button
                class="button button-secondary"
                :disabled="state.page === 0"
                @click="catalog.loadCases(state.page - 1)"
              >
                上一页</button
              ><button
                class="button button-secondary"
                :disabled="(state.page + 1) * state.size >= state.total"
                @click="catalog.loadCases(state.page + 1)"
              >
                下一页
              </button>
            </div>
          </nav>
        </section>
      </div>
    </div>
  </div>
</template>
