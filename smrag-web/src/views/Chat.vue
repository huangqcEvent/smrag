<template>
  <section class="chat-page">
    <header class="workspace-header">
      <div>
        <h1>智能问答</h1>
        <p>{{ chatHistory.length ? `${chatHistory.length} 轮对话` : '新会话' }}</p>
      </div>
      <el-tooltip content="清空当前会话" placement="bottom">
        <el-button
          class="icon-button"
          text
          circle
          aria-label="清空当前会话"
          @click="clearSession"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
      </el-tooltip>
    </header>

    <p class="sr-only" role="status" aria-live="polite">{{ liveStatus }}</p>

    <div ref="messagesContainer" class="messages-container">
      <div v-if="chatHistory.length === 0" class="empty-state">
        <div class="empty-symbol" aria-hidden="true">
          <el-icon><ChatDotRound /></el-icon>
        </div>
        <h2>从一个问题开始</h2>
        <div class="starter-list">
          <button
            v-for="starter in starters"
            :key="starter"
            type="button"
            @click="quickAsk(starter)"
          >
            <span>{{ starter }}</span>
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
      </div>

      <article
        v-for="(item, index) in chatHistory"
        :key="item.sessionId + '-' + index"
        class="message-pair"
      >
        <div class="user-message">
          <div class="message-meta">
            <span>你</span>
            <time>{{ formatTime(item.createTime) }}</time>
          </div>
          <p>{{ item.question }}</p>
        </div>

        <div class="assistant-message">
          <div class="message-meta assistant-meta">
            <span class="assistant-name">
              <el-icon><MagicStick /></el-icon>
              AI 助手
            </span>
            <span class="route-label">{{ getRouteLabel(item.routeType) }}</span>
          </div>
          <div class="assistant-content">
            <div v-if="item.answer" class="streamed-answer">
              <p v-if="isGenerating(item)" class="stream-plain-text">
                {{ item.answer }}<span class="stream-cursor" aria-hidden="true"></span>
              </p>
              <MarkdownContent v-else :content="item.answer" />
            </div>
            <div v-if="isGenerating(item) && !item.answer" class="thinking-state" aria-hidden="true">
              <span class="thinking-dot"></span>
              <span class="thinking-dot"></span>
              <span class="thinking-dot"></span>
              <span>{{ getPhaseLabel(item.phase) }}</span>
            </div>
            <p v-else-if="item.state === 'error'" class="stream-message stream-error">
              {{ item.error || '生成回答失败' }}
            </p>
            <p v-else-if="item.state === 'stopped'" class="stream-message">已停止生成</p>
          </div>

          <details v-if="item.citations?.length" class="citations">
            <summary>
              <span><el-icon><Document /></el-icon>引用依据</span>
              <span class="citation-count">{{ item.citations.length }}</span>
            </summary>
            <div class="citations-content">
              <ol>
                <li v-for="citation in item.citations" :key="citation.vectorId">
                  <div class="citation-heading">
                    <strong>{{ citation.documentName || `文档 ${citation.documentId}` }}</strong>
                    <span v-if="citation.pageNumber != null">第 {{ citation.pageNumber }} 页</span>
                    <span v-if="citation.score != null">
                      相关度 {{ formatScore(citation.score) }}
                    </span>
                  </div>
                  <p>{{ citation.excerpt }}</p>
                </li>
              </ol>
            </div>
          </details>

          <div v-else-if="item.references?.length" class="legacy-references">
            <el-icon><Document /></el-icon>
            <span>{{ item.references.join('、') }}</span>
          </div>
        </div>
      </article>

    </div>

    <footer class="composer-dock">
      <div class="scope-row">
        <el-icon><Collection /></el-icon>
        <label for="knowledge-base">知识库</label>
        <el-input
          id="knowledge-base"
          v-model="knowledgeBaseId"
          size="small"
          maxlength="64"
          aria-label="知识库 ID"
        />
      </div>
      <div class="composer">
        <el-input
          v-model="question"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 4 }"
          resize="none"
          maxlength="4000"
          placeholder="输入问题"
          :disabled="loading"
          aria-label="问题"
          @keydown.enter.exact.prevent="handleSend"
        />
        <el-tooltip :content="loading ? '停止生成' : '发送'" placement="top">
          <el-button
            :class="['send-button', { 'stop-button': loading }]"
            type="primary"
            circle
            :disabled="!loading && !question.trim()"
            :aria-label="loading ? '停止生成' : '发送问题'"
            @click="handleComposerAction"
          >
            <el-icon><VideoPause v-if="loading" /><Promotion v-else /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </footer>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import {
  ArrowRight,
  ChatDotRound,
  Collection,
  Delete,
  Document,
  MagicStick,
  Promotion,
  VideoPause
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MarkdownContent from '@/components/MarkdownContent.vue'
import { useChatStore } from '@/stores/chat'

const chatStore = useChatStore()
const question = ref('')
const knowledgeBaseId = ref('default')
const messagesContainer = ref(null)
const { chatHistory, loading } = storeToRefs(chatStore)
const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
let scrollFrame = 0

const starters = [
  '知识库中的风险揭示要求是什么？',
  '查询某只基金产品的风险等级',
  '对比两只基金的最低投资金额',
  '生成一段合规的客户沟通话术'
]

onMounted(() => chatStore.initSession())

onBeforeUnmount(() => {
  if (scrollFrame) cancelAnimationFrame(scrollFrame)
  chatStore.stopGeneration()
})

watch(
  () => {
    const last = chatHistory.value.at(-1)
    return [chatHistory.value.length, last?.answer?.length, last?.state, last?.citations?.length]
  },
  () => scheduleScrollToBottom(),
  { flush: 'post' }
)

const handleSend = async () => {
  const value = question.value.trim()
  if (!value || loading.value) return

  question.value = ''
  try {
    await chatStore.askQuestion(value, {
      knowledgeBaseId: knowledgeBaseId.value.trim() || 'default'
    })
  } catch {
    question.value = value
  }
}

const handleComposerAction = () => {
  if (loading.value) {
    chatStore.stopGeneration()
    return
  }
  handleSend()
}

const quickAsk = (value) => {
  question.value = value
  handleSend()
}

const clearSession = () => {
  chatStore.clearSession()
  ElMessage.success('已开始新会话')
}

const scheduleScrollToBottom = () => {
  if (scrollFrame) return
  scrollFrame = requestAnimationFrame(async () => {
    scrollFrame = 0
    await nextTick()
    const container = messagesContainer.value
    if (!container) return
    container.scrollTo({
      top: container.scrollHeight,
      behavior: prefersReducedMotion.matches || loading.value ? 'auto' : 'smooth'
    })
  })
}

const formatTime = (time) => {
  const date = time ? new Date(time) : new Date()
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const formatScore = (score) => `${Math.round(Number(score) * 100)}%`

const isGenerating = (item) => item.state === 'pending' || item.state === 'streaming'

const getPhaseLabel = (phase) => ({
  ANALYZING: '正在分析问题',
  RETRIEVING: '正在检索知识库',
  CALLING_TOOL: '正在查询业务数据',
  GENERATING: '正在生成回答'
}[phase] || '正在生成回答')

const getRouteLabel = (routeType) => ({
  KNOWLEDGE_QA: '知识问答',
  PRODUCT_QUERY: '产品查询',
  PRODUCT_COMPARE: '产品对比',
  PRODUCT_RECOMMEND: '产品推荐',
  MANAGER_QUERY: '基金经理',
  SALES_SCRIPT: '销售话术'
}[routeType] || '智能回答')

const liveStatus = computed(() => {
  const last = chatHistory.value.at(-1)
  if (!last) return ''
  if (isGenerating(last)) return getPhaseLabel(last.phase)
  if (last.state === 'done') return '回答生成完成'
  if (last.state === 'error') return '回答生成失败'
  if (last.state === 'stopped') return '已停止生成'
  return ''
})
</script>

<style scoped>
.chat-page {
  height: 100%;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  background: var(--bg-primary);
}

.workspace-header {
  min-height: 76px;
  padding: 16px clamp(20px, 4vw, 48px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 88%, transparent);
  backdrop-filter: blur(18px) saturate(150%);
  z-index: 2;
}

.workspace-header h1 {
  margin: 0;
  font-size: 1.25rem;
  line-height: 1.25;
  font-weight: 650;
  letter-spacing: 0;
}

.workspace-header p {
  margin: 3px 0 0;
  color: var(--text-muted);
  font-size: 0.78rem;
}

.icon-button {
  width: 36px;
  height: 36px;
  color: var(--text-secondary);
}

.messages-container {
  width: min(100%, 860px);
  margin: 0 auto;
  padding: 32px clamp(16px, 4vw, 36px) 48px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.empty-state {
  min-height: 100%;
  display: grid;
  place-content: center;
  justify-items: center;
  padding: 32px 0;
}

.empty-symbol {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  margin-bottom: 18px;
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 1.5rem;
}

.empty-state h2 {
  margin: 0 0 24px;
  font-size: 1.25rem;
  font-weight: 620;
  letter-spacing: 0;
}

.starter-list {
  width: min(100%, 520px);
  border-top: 1px solid var(--border);
}

.starter-list button {
  width: 100%;
  min-height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 4px;
  border: 0;
  border-bottom: 1px solid var(--border);
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: color 160ms ease-out, padding 160ms ease-out;
}

.starter-list button:hover {
  color: var(--accent);
  padding-left: 8px;
  padding-right: 8px;
}

.starter-list button:active {
  transform: scale(0.99);
}

.message-pair + .message-pair {
  margin-top: 32px;
  padding-top: 32px;
  border-top: 1px solid var(--border-subtle);
}

.user-message {
  width: min(78%, 600px);
  margin-left: auto;
}

.message-meta {
  min-height: 24px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-bottom: 6px;
  color: var(--text-muted);
  font-size: 0.75rem;
}

.user-message p {
  margin: 0;
  padding: 12px 16px;
  border-radius: 8px;
  background: var(--user-message);
  color: var(--text-primary);
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.assistant-message {
  margin-top: 22px;
}

.assistant-meta {
  justify-content: flex-start;
  margin-bottom: 10px;
}

.assistant-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-primary);
  font-weight: 600;
}

.assistant-name .el-icon {
  color: var(--accent);
}

.route-label {
  padding: 2px 7px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 0.68rem;
  font-weight: 600;
}

.assistant-content {
  min-height: 28px;
}

.streamed-answer {
  position: relative;
  min-width: 0;
}

.stream-plain-text {
  max-width: 72ch;
  margin: 0;
  color: var(--text-primary);
  font-size: 0.98rem;
  line-height: 1.75;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.stream-cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 3px;
  vertical-align: -0.12em;
  background: var(--accent);
  animation: cursor-blink 900ms steps(1, end) infinite;
}

@keyframes cursor-blink {
  50% { opacity: 0; }
}

.citations {
  margin-top: 20px;
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
}

.citations summary {
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  color: var(--text-secondary);
  font-size: 0.82rem;
  list-style: none;
}

.citations summary::-webkit-details-marker {
  display: none;
}

.citations summary > span:first-child {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-weight: 600;
}

.citation-count {
  min-width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--surface-raised);
  color: var(--text-muted);
  font-size: 0.72rem;
}

.citations-content {
  max-height: 0;
  opacity: 0;
  overflow: hidden;
  transition: max-height 180ms ease-out, opacity 180ms ease-out;
}

.citations[open] .citations-content {
  max-height: 1000px;
  opacity: 1;
}

.citations ol {
  margin: 0;
  padding: 0;
  list-style: none;
}

.citations li {
  padding: 13px 0 15px;
  border-top: 1px solid var(--border-subtle);
}

.citation-heading {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-bottom: 5px;
  font-size: 0.78rem;
}

.citation-heading strong {
  min-width: 0;
  color: var(--text-primary);
  overflow-wrap: anywhere;
}

.citation-heading span {
  color: var(--text-muted);
}

.citations li p {
  max-width: 76ch;
  margin: 0;
  color: var(--text-secondary);
  font-size: 0.8rem;
  line-height: 1.55;
}

.legacy-references {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 18px;
  color: var(--text-muted);
  font-size: 0.8rem;
}

.thinking-state {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 24px;
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 0.8rem;
}

.stream-message {
  margin: 8px 0 0;
  color: var(--text-muted);
  font-size: 0.78rem;
}

.stream-error {
  color: var(--el-color-danger);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.thinking-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent);
  animation: thinking 1.15s ease-in-out infinite;
}

.thinking-dot:nth-child(2) { animation-delay: 120ms; }
.thinking-dot:nth-child(3) { animation-delay: 240ms; }

@keyframes thinking {
  0%, 70%, 100% { opacity: 0.3; transform: translateY(0); }
  35% { opacity: 1; transform: translateY(-3px); }
}

.composer-dock {
  padding: 10px clamp(16px, 4vw, 36px) 18px;
  border-top: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 90%, transparent);
  backdrop-filter: blur(20px) saturate(150%);
}

.scope-row,
.composer {
  width: min(100%, 788px);
  margin: 0 auto;
}

.scope-row {
  min-height: 30px;
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--text-muted);
  font-size: 0.72rem;
}

.scope-row .el-input {
  width: 130px;
}

.composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 40px;
  align-items: end;
  gap: 10px;
  padding: 9px 10px 9px 14px;
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  background: var(--surface-raised);
  box-shadow: var(--shadow-low);
  transition: border-color 160ms ease-out, box-shadow 160ms ease-out;
}

.composer:focus-within {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-focus), var(--shadow-low);
}

.composer :deep(.el-textarea__inner) {
  min-height: 38px !important;
  padding: 8px 0;
  border: 0;
  box-shadow: none;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  line-height: 1.45;
}

.send-button {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
}

.stop-button {
  --el-button-bg-color: var(--danger);
  --el-button-border-color: var(--danger);
  --el-button-hover-bg-color: color-mix(in srgb, var(--danger) 84%, black);
  --el-button-hover-border-color: color-mix(in srgb, var(--danger) 84%, black);
  --el-button-active-bg-color: color-mix(in srgb, var(--danger) 76%, black);
  --el-button-active-border-color: color-mix(in srgb, var(--danger) 76%, black);
}

@media (max-width: 640px) {
  .workspace-header {
    min-height: 64px;
  }

  .messages-container {
    padding-top: 24px;
  }

  .user-message {
    width: 88%;
  }

  .composer-dock {
    padding-bottom: max(12px, env(safe-area-inset-bottom));
  }
}

@media (prefers-reduced-motion: reduce) {
  .thinking-dot,
  .stream-cursor {
    animation: none;
  }

  .messages-container {
    scroll-behavior: auto;
  }

  .citations-content {
    transition: none;
  }
}

@media (prefers-reduced-transparency: reduce) {
  .workspace-header,
  .composer-dock {
    background: var(--surface);
    backdrop-filter: none;
  }
}
</style>
