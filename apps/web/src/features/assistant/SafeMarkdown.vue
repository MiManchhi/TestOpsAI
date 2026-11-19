<script setup lang="ts">
import { computed } from 'vue';
const props = defineProps<{ text: string }>();
// A small text-only Markdown view. No HTML, image loading, or model-provided links execute.
const blocks = computed(() => {
  const result: Array<{ kind: 'heading' | 'paragraph' | 'list' | 'code'; text: string }> = [];
  let code = false,
    buffer: string[] = [];
  const flush = () => {
    if (buffer.length) {
      result.push({ kind: code ? 'code' : 'paragraph', text: buffer.join('\n') });
      buffer = [];
    }
  };
  for (const line of props.text.replace(/\r\n/g, '\n').split('\n')) {
    if (line.startsWith('```')) {
      flush();
      code = !code;
    } else if (code) buffer.push(line);
    else if (!line.trim()) flush();
    else if (/^#{1,6}\s/.test(line)) {
      flush();
      result.push({ kind: 'heading', text: line.replace(/^#{1,6}\s+/, '') });
    } else if (/^[-*]\s/.test(line)) {
      flush();
      result.push({ kind: 'list', text: line.replace(/^[-*]\s+/, '') });
    } else buffer.push(line);
  }
  flush();
  return result;
});
function inline(text: string) {
  return text
    .split(/(\*\*[^*]+\*\*|`[^`]+`)/g)
    .filter(Boolean)
    .map((part) =>
      part.startsWith('**') && part.endsWith('**')
        ? { tag: 'strong', text: part.slice(2, -2) }
        : part.startsWith('`') && part.endsWith('`')
          ? { tag: 'code', text: part.slice(1, -1) }
          : { tag: 'span', text: part },
    );
}
</script>
<template>
  <div class="safe-markdown">
    <template v-for="(block, index) in blocks" :key="index">
      <pre v-if="block.kind === 'code'"><code>{{ block.text }}</code></pre>
      <h3 v-else-if="block.kind === 'heading'">{{ block.text }}</h3>
      <p v-else :class="{ 'markdown-list-item': block.kind === 'list' }">
        <span v-if="block.kind === 'list'" aria-hidden="true">• </span
        ><component
          :is="part.tag"
          v-for="(part, partIndex) in inline(block.text)"
          :key="partIndex"
          >{{ part.text }}</component
        >
      </p></template
    >
  </div>
</template>
