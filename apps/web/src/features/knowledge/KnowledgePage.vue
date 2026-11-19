<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { projects } from '../projects/context';
import { categories, createKnowledgeBase, listKnowledgeBases, type Category } from './api';
import { idempotencyKey, useProjectAction, useResource } from '../../app/resource';
import RequestState from '../../app/RequestState.vue';
import PageControls from '../../app/PageControls.vue';
import type { KnowledgeBaseCreate } from '../../app/contracts';
const router = useRouter();
const page = ref(0),
  search = ref(''),
  query = ref('');
const category = ref<Category | ''>('');
const canWrite = computed(() => projects.current.value?.role !== 'VIEWER');
const dialog = ref<HTMLDialogElement>();
const form = reactive<KnowledgeBaseCreate>({
  name: '',
  description: '',
  category: 'METERING',
  visibility: 'PROJECT',
});
const action = useProjectAction(),
  key = idempotencyKey();
watch(
  [query, category, () => projects.state.selectedId],
  () => {
    page.value = 0;
  },
  { flush: 'sync' },
);
watch(
  () => projects.state.selectedId,
  () => {
    search.value = query.value = '';
    category.value = '';
    dialog.value?.close();
    Object.assign(form, { name: '', description: '', category: 'METERING', visibility: 'PROJECT' });
  },
  { flush: 'sync' },
);
const libraries = useResource(
  () => `${page.value}:${query.value}:${category.value}`,
  (signal) =>
    listKnowledgeBases(projects.state.selectedId, page.value, query.value, category.value, signal),
);
function submit() {
  const pid = projects.state.selectedId,
    body = { ...form };
  void action.run(
    (signal) => createKnowledgeBase(pid, body, key(JSON.stringify([pid, body])), signal),
    async (value) => {
      dialog.value?.close();
      await router.push({ path: `/knowledge/${value.id}`, query: { projectId: pid } });
    },
  );
}
</script>
<template>
  <div class="flow-page">
    <header class="page-heading-row">
      <div>
        <p class="section-kicker">KNOWLEDGE / 规范与依据</p>
        <h1 data-page-heading tabindex="-1">知识库</h1>
        <p class="muted">按测试领域组织资料，明确版本和可读范围。</p>
      </div>
      <button v-if="canWrite" class="button button-primary" @click="dialog?.showModal()">
        创建知识库
      </button>
    </header>
    <section class="panel flow-panel">
      <form class="flow-toolbar" @submit.prevent="query = search.trim()">
        <label>搜索知识库<input v-model="search" type="search" placeholder="名称或用途" /></label
        ><label
          >领域<select v-model="category" aria-label="领域">
            <option value="">全部领域</option>
            <option v-for="(label, value) in categories" :key="value" :value="value">
              {{ label }}
            </option>
          </select></label
        >
        <div class="flow-actions">
          <button class="button button-primary" type="submit">搜索</button
          ><button
            class="button button-secondary"
            type="button"
            @click="
              search = query = '';
              category = '';
            "
          >
            清空条件
          </button>
        </div>
      </form>
      <RequestState
        :loading="libraries.loading.value"
        :error="libraries.error.value"
        :trace="libraries.trace.value"
        @retry="libraries.reload"
      />
      <template v-if="libraries.data.value"
        ><p v-if="!libraries.data.value.items.length" class="flow-empty">
          {{
            query || category
              ? '没有符合条件的知识库，试试清空条件。'
              : '暂无可访问知识库，可创建资料库或联系项目所有者。'
          }}
        </p>
        <div v-else class="knowledge-grid">
          <article v-for="kb in libraries.data.value.items" :key="kb.id" class="knowledge-card">
            <div class="panel-heading">
              <span class="section-kicker">{{ categories[kb.category] }}</span><span
                class="status-tag"
                >{{ kb.visibility === 'PROJECT' ? '项目成员可读' : '限制访问' }}</span
              >
            </div>
            <RouterLink
              class="flow-title-link"
              :to="{ path: '/knowledge/' + kb.id, query: { projectId: projects.state.selectedId } }"
              >{{ kb.name }}</RouterLink
            >
            <p class="muted preserve-text">{{ kb.description || '暂无用途说明。' }}</p>
            <div class="knowledge-card-footer">
              <span>{{ kb.readyCount }} / {{ kb.documentCount }} 份文档可用</span
              ><RouterLink
                v-if="kb.readyCount"
                class="text-button"
                :to="{
                  path: '/assistant',
                  query: { projectId: projects.state.selectedId, kbId: kb.id },
                }"
                >问此库 →</RouterLink
              ><span v-else class="muted">等待文档就绪</span>
            </div>
          </article>
        </div>
        <PageControls
          :page="page"
          :size="20"
          :total="libraries.data.value.total"
          @change="page = $event"
      /></template>
    </section>
    <dialog ref="dialog" class="flow-dialog" aria-labelledby="create-kb-title">
      <h2 id="create-kb-title">创建知识库</h2>
      <form class="flow-form" @submit.prevent="submit">
        <fieldset :disabled="action.busy.value">
          <label>知识库名称<input v-model="form.name" required maxlength="128" /></label
          ><label>用途说明<textarea v-model="form.description" maxlength="1000" rows="3" /></label
          ><label
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
          <p v-if="action.error.value" class="flow-error" role="alert">{{ action.error.value }}</p>
          <div class="flow-actions">
            <button class="button button-secondary" type="button" @click="dialog?.close()">
              取消</button
            ><button class="button button-primary" type="submit">创建知识库</button>
          </div>
        </fieldset>
      </form>
    </dialog>
  </div>
</template>
