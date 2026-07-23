<template>
  <teleport to="body">
    <div v-if="document" class="viewer-backdrop" @click.self="$emit('close')">
      <section class="viewer-panel" role="dialog" aria-modal="true" :aria-label="document.name">
        <header>
          <div>
            <strong>{{ document.name }}</strong>
            <span>{{ typeLabel }}</span>
          </div>
          <div class="viewer-actions">
            <el-button text :disabled="loading || !objectUrl" @click="download">下载</el-button>
            <el-button text circle aria-label="关闭文档" @click="$emit('close')">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </header>

        <div v-if="loading" class="viewer-state">正在加载文档…</div>
        <div v-else-if="error" class="viewer-state error">
          <strong>无法打开文档</strong>
          <span>{{ error }}</span>
        </div>
        <iframe
          v-else-if="isPdf"
          class="pdf-frame"
          :src="objectUrl"
          :title="document.name"
        />
        <pre v-else-if="isText" class="text-reader">{{ textContent }}</pre>
        <div v-else class="viewer-state">
          <strong>此格式暂不支持在线预览</strong>
          <span>可以下载后使用本地应用打开。</span>
          <el-button type="primary" @click="download">下载文档</el-button>
        </div>
      </section>
    </div>
  </teleport>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { getDocumentFile } from '@/api/knowledge'

const props = defineProps({ document: { type: Object, default: null } })
defineEmits(['close'])

const loading = ref(false)
const error = ref('')
const objectUrl = ref('')
const textContent = ref('')
const resolvedType = computed(() => {
  const explicit = String(props.document?.fileType || '').toUpperCase()
  if (explicit) return explicit
  const name = String(props.document?.name || '').toLowerCase()
  if (name.endsWith('.pdf')) return 'PDF'
  if (name.endsWith('.txt')) return 'TXT'
  return 'WORD'
})
const isPdf = computed(() => resolvedType.value === 'PDF')
const isText = computed(() => resolvedType.value === 'TXT')
const typeLabel = computed(() => isPdf.value ? 'PDF 预览' : isText.value ? '文本预览' : '原始文档')

const clearObjectUrl = () => {
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value)
  objectUrl.value = ''
  textContent.value = ''
}

watch(() => props.document, async (document) => {
  clearObjectUrl()
  error.value = ''
  if (!document) return
  loading.value = true
  try {
    const blob = await getDocumentFile(document.id)
    objectUrl.value = URL.createObjectURL(blob)
    if (resolvedType.value === 'TXT') {
      textContent.value = await blob.text()
    }
  } catch (failure) {
    error.value = failure?.response?.status === 403
      ? '你没有查看该文档的权限。'
      : '文档读取失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}, { immediate: true })

const download = () => {
  if (!objectUrl.value) return
  const anchor = window.document.createElement('a')
  anchor.href = objectUrl.value
  anchor.download = props.document?.name || 'document'
  anchor.click()
}

onBeforeUnmount(clearObjectUrl)
</script>

<style scoped>
.viewer-backdrop {
  position: fixed;
  inset: 0;
  z-index: 2100;
  display: grid;
  place-items: center;
  padding: 24px;
  background: color-mix(in srgb, var(--text-primary) 28%, transparent);
}

.viewer-panel {
  width: min(1100px, 100%);
  height: min(88vh, 860px);
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: 0 24px 70px rgba(20, 24, 35, 0.24);
}

.viewer-panel header {
  min-height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 14px 10px 18px;
  border-bottom: 1px solid var(--border);
}

.viewer-panel header > div:first-child {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.viewer-panel strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.viewer-panel header span {
  color: var(--text-muted);
  font-size: 0.72rem;
}

.viewer-actions {
  display: flex;
  align-items: center;
}

.pdf-frame {
  width: 100%;
  height: 100%;
  border: 0;
  background: var(--bg-primary);
}

.text-reader {
  margin: 0;
  padding: clamp(20px, 4vw, 48px);
  overflow: auto;
  color: var(--text-primary);
  font: 0.9rem/1.75 ui-monospace, SFMono-Regular, Consolas, monospace;
  white-space: pre-wrap;
}

.viewer-state {
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 10px;
  color: var(--text-muted);
  text-align: center;
}

.viewer-state.error strong { color: var(--danger); }

@media (max-width: 700px) {
  .viewer-backdrop { padding: 0; }
  .viewer-panel { width: 100%; height: 100%; border: 0; border-radius: 0; }
}
</style>
