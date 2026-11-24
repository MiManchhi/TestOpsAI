<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import { errorMessage } from '../../app/http';
import type { DocumentKind, WorkspaceDocument } from '../../app/workspace-contracts';
import { submitVersion, uploadAttachment } from './workspace-api';

const props = defineProps<{
  projectId: string;
  document?: WorkspaceDocument;
  attachmentId?: string;
}>();
const emit = defineEmits<{ saved: [WorkspaceDocument]; cancel: [] }>();
const title = ref(props.document?.title ?? '');
const kind = ref<DocumentKind>(props.document?.kind ?? 'SPEC');
const versionLabel = ref('');
const changeNote = ref('');
const file = ref<File>();
const inherited = ref(props.attachmentId ?? '');
const busy = ref(false);
const error = ref('');
const signal = new AbortController();
let uploadKey = '';
let submitKey = '';
onBeforeUnmount(() => signal.abort());

function pickFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0];
  uploadKey = submitKey = '';
  if (file.value) inherited.value = '';
}

async function submit() {
  if (busy.value || (!file.value && !inherited.value)) return;
  if (!title.value.trim() || !versionLabel.value.trim()) {
    error.value = '请填写资料名称与版本号。';
    return;
  }
  error.value = '';
  busy.value = true;
  try {
    let attachmentId = inherited.value;
    if (file.value) {
      uploadKey ||= crypto.randomUUID();
      const attachment = await uploadAttachment(
        props.projectId,
        file.value,
        uploadKey,
        signal.signal,
      );
      attachmentId = attachment.attachmentId;
    }
    submitKey ||= crypto.randomUUID();
    const result = await submitVersion(
      props.projectId,
      {
        title: title.value.trim(),
        kind: kind.value,
        versionLabel: versionLabel.value.trim(),
        changeNote: changeNote.value.trim(),
        attachmentId,
        ...(props.document ? { documentId: props.document.documentId } : {}),
      },
      submitKey,
      signal.signal,
    );
    emit('saved', result);
  } catch (failure) {
    if (!signal.signal.aborted) error.value = errorMessage(failure);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <form class="detail-panel submission-form" @submit.prevent="submit" @input="submitKey = ''">
    <h2>{{ document ? '提交新版本' : '提交资料' }}</h2>
    <label
      >资料名称 <input v-model="title" maxlength="160" required :disabled="busy || !!document"
    /></label>
    <label
      >资料类型
      <select v-model="kind" :disabled="busy || !!document">
        <option value="SPEC">技术规范</option>
        <option value="TENDER">标书</option>
        <option value="KEYWORDS">关键字说明</option>
      </select>
    </label>
    <label
      >版本号
      <input v-model="versionLabel" maxlength="160" placeholder="例如 V1" required :disabled="busy"
    /></label>
    <label
      >PDF、DOCX 或 Markdown 原件
      <input
        type="file"
        accept=".pdf,.docx,.md,.markdown"
        :required="!inherited"
        :disabled="busy"
        @change="pickFile"
      />
    </label>
    <p v-if="inherited">已带入对话中的原件。选择新文件可替换。</p>
    <label>变更说明 <textarea v-model="changeNote" rows="2" :disabled="busy" /></label>
    <p v-if="error" role="alert">{{ error }}</p>
    <div class="button-row">
      <button class="button button-primary" :disabled="busy">
        {{ busy ? '正在提交…' : '提交审核' }}
      </button>
      <button
        type="button"
        class="button button-secondary"
        :disabled="busy"
        @click="emit('cancel')"
      >
        取消
      </button>
    </div>
  </form>
</template>

<style scoped>
.submission-form,
.submission-form label {
  display: grid;
  gap: 0.6rem;
}
.button-row {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}
input,
select,
button {
  min-height: 44px;
}
</style>
