<template>
  <div class="markdown-content" v-html="html"></div>
</template>

<script setup>
import { computed } from 'vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'

const props = defineProps({
  content: {
    type: String,
    default: ''
  }
})

const sanitizeOptions = {
  ALLOWED_TAGS: [
    'p', 'br', 'strong', 'em', 'del', 'ul', 'ol', 'li', 'blockquote',
    'pre', 'code', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'hr',
    'table', 'thead', 'tbody', 'tr', 'th', 'td', 'a'
  ],
  ALLOWED_ATTR: ['href', 'title'],
  ALLOW_DATA_ATTR: false,
  FORBID_TAGS: [
    'style', 'img', 'svg', 'math', 'form', 'input', 'button', 'iframe',
    'object', 'embed', 'video', 'audio', 'source'
  ],
  FORBID_ATTR: ['style', 'src', 'srcset'],
  ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|[\/#])/i
}

const html = computed(() => DOMPurify.sanitize(
  marked.parse(props.content, { breaks: true, gfm: true }),
  sanitizeOptions
))
</script>

<style scoped>
.markdown-content {
  max-width: 72ch;
  overflow-wrap: anywhere;
  color: var(--text-primary);
  font-size: 0.98rem;
  line-height: 1.75;
}

.markdown-content :deep(> :first-child) {
  margin-top: 0;
}

.markdown-content :deep(> :last-child) {
  margin-bottom: 0;
}

.markdown-content :deep(p),
.markdown-content :deep(ul),
.markdown-content :deep(ol),
.markdown-content :deep(blockquote),
.markdown-content :deep(pre),
.markdown-content :deep(table) {
  margin: 0.75em 0;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4) {
  margin: 1.15em 0 0.45em;
  font-size: 1em;
  line-height: 1.4;
  font-weight: 650;
  letter-spacing: 0;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  padding-left: 1.4em;
}

.markdown-content :deep(li + li) {
  margin-top: 0.25em;
}

.markdown-content :deep(blockquote) {
  padding: 0.35em 0.9em;
  border: 1px solid var(--border);
  border-radius: 6px;
  color: var(--text-secondary);
  background: var(--surface-raised);
}

.markdown-content :deep(code) {
  padding: 0.12em 0.32em;
  border-radius: 4px;
  background: var(--surface-raised);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 0.88em;
}

.markdown-content :deep(pre) {
  overflow-x: auto;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--surface-raised);
}

.markdown-content :deep(pre code) {
  padding: 0;
  background: transparent;
}

.markdown-content :deep(a) {
  color: var(--accent);
  text-underline-offset: 3px;
}

.markdown-content :deep(table) {
  display: block;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  padding: 7px 10px;
  border: 1px solid var(--border);
  text-align: left;
}
</style>
