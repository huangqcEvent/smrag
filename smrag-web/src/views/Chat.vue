<template>
  <div class="chat-page">
    <div class="chat-container">
      <!-- 页面标题 -->
      <div class="page-header">
        <h2 class="page-title glow-text">智能问答</h2>
        <p class="page-subtitle">AI驱动的私募基金智能助手</p>
        <el-button
          type="primary"
          @click="clearSession"
          size="small"
          class="clear-btn"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
          </svg>
          清空会话
        </el-button>
      </div>

      <!-- 聊天消息区域 -->
      <div class="chat-messages" ref="messagesContainer">
        <div v-if="chatHistory.length === 0" class="empty-state">
          <div class="empty-icon">
            <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1">
              <circle cx="12" cy="12" r="10"/>
              <path d="M8 14s1.5 2 4 2 4-2 4-2"/>
              <line x1="9" y1="9" x2="9.01" y2="9"/>
              <line x1="15" y1="9" x2="15.01" y2="9"/>
            </svg>
          </div>
          <p class="empty-text">开始提问，获取专业私募基金建议</p>
        </div>

        <div v-for="(item, index) in chatHistory" :key="index" class="message-group fade-in">
          <!-- 用户问题 -->
          <div class="message-item user-message">
            <div class="message-avatar user-avatar">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
            </div>
            <div class="message-content">
              <div class="message-label">您的提问</div>
              <div class="message-text">{{ item.question }}</div>
            </div>
          </div>

          <!-- AI回答 -->
          <div class="message-item ai-message">
            <div class="message-avatar ai-avatar glow-border">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
              </svg>
            </div>
            <div class="message-content">
              <div class="message-label">
                AI回答
                <el-tag size="small" :type="getRouteTagType(item.routeType)" class="route-tag">
                  {{ getRouteLabel(item.routeType) }}
                </el-tag>
              </div>
              <div class="message-text">{{ item.answer }}</div>

              <!-- 参考资料 -->
              <div v-if="item.references && item.references.length > 0" class="references-section">
                <div class="divider"></div>
                <div class="references-title">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <polyline points="14 2 14 8 20 8"/>
                    <line x1="16" y1="13" x2="8" y2="13"/>
                    <line x1="16" y1="17" x2="8" y2="17"/>
                  </svg>
                  参考资料
                </div>
                <div class="references-tags">
                  <el-tag v-for="(ref, i) in item.references" :key="i" size="small" class="ref-tag">
                    {{ ref }}
                  </el-tag>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 加载状态 -->
        <div v-if="loading" class="loading-indicator">
          <div class="loading-spinner"></div>
          <span class="loading-text">AI正在思考...</span>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="chat-input-area">
        <div class="input-container">
          <el-input
            v-model="question"
            placeholder="请输入您的问题，例如：什么是量化对冲策略？"
            @keyup.enter="handleSend"
            :disabled="loading"
            size="large"
            class="chat-input"
          >
            <template #append>
              <el-button
                @click="handleSend"
                :loading="loading"
                type="primary"
                class="send-btn"
              >
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="22" y2="2" x2="11" y2="13"/>
                  <polygon points="22 2 15 22 11 13 2 9 22 2"/>
                </svg>
                发送
              </el-button>
            </template>
          </el-input>
        </div>

        <!-- 快捷标签 -->
        <div class="quick-tags">
          <el-tag
            v-for="(tag, index) in quickTags"
            :key="index"
            :type="tag.type"
            size="small"
            class="quick-tag"
            @click="quickAsk(tag.text)"
          >
            {{ tag.label }}
          </el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useChatStore } from '@/stores/chat'
import { ElMessage } from 'element-plus'

const chatStore = useChatStore()
const question = ref('')
const messagesContainer = ref(null)

const { chatHistory, loading, askQuestion, initSession } = chatStore

const quickTags = [
  { label: '知识问答', type: 'success', text: '什么是量化对冲策略？' },
  { label: '产品查询', type: 'warning', text: '查询高风险产品' },
  { label: '产品对比', type: 'danger', text: '对比两只基金的收益率' },
  { label: '产品推荐', type: 'info', text: '推荐适合新手的产品' },
  { label: '销售话术', text: '生成私募基金销售话术' }
]

onMounted(() => {
  initSession()
})

const handleSend = async () => {
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }

  const q = question.value
  question.value = ''

  await askQuestion(q)
  scrollToBottom()
}

const quickAsk = (text) => {
  question.value = text
  handleSend()
}

const clearSession = () => {
  chatStore.clearSession()
  ElMessage.success('会话已清空')
}

const scrollToBottom = () => {
  setTimeout(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  }, 100)
}

const getRouteLabel = (routeType) => {
  const labels = {
    KNOWLEDGE_QA: '知识问答',
    PRODUCT_QUERY: '产品查询',
    PRODUCT_COMPARE: '产品对比',
    PRODUCT_RECOMMEND: '产品推荐',
    SALES_SCRIPT: '销售话术'
  }
  return labels[routeType] || routeType
}

const getRouteTagType = (routeType) => {
  const types = {
    KNOWLEDGE_QA: 'success',
    PRODUCT_QUERY: 'warning',
    PRODUCT_COMPARE: 'danger',
    PRODUCT_RECOMMEND: 'info',
    SALES_SCRIPT: ''
  }
  return types[routeType] || 'info'
}
</script>

<style scoped>
.chat-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
}

.chat-container {
  max-width: 900px;
  margin: 0 auto;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 2rem;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.5rem 0;
  border-bottom: 1px solid var(--border);
  margin-bottom: 1.5rem;
}

.page-title {
  font-family: var(--font-code);
  font-size: 2rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.page-subtitle {
  color: var(--text-secondary);
  font-size: 0.875rem;
  margin: 0.25rem 0 0;
}

.clear-btn {
  cursor: pointer;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 1rem 0;
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem;
  color: var(--text-muted);
}

.empty-icon {
  opacity: 0.5;
  margin-bottom: 1rem;
}

.empty-text {
  font-size: 1rem;
}

.message-group {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.message-item {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
}

.message-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  flex-shrink: 0;
}

.user-avatar {
  background: var(--bg-secondary);
  color: var(--text-secondary);
  border: 1px solid var(--border);
}

.ai-avatar {
  background: linear-gradient(135deg, var(--primary), var(--cta));
  color: var(--text-primary);
}

.message-content {
  flex: 1;
  background: var(--bg-card);
  border: 1px solid var(--border);
  padding: 1rem 1.25rem;
  border-radius: var(--radius-lg);
}

.message-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.75rem;
  color: var(--text-muted);
  margin-bottom: 0.75rem;
}

.message-text {
  font-size: 1rem;
  color: var(--text-primary);
  line-height: 1.6;
}

.route-tag {
  cursor: default;
}

.references-section {
  margin-top: 1rem;
}

.divider {
  height: 1px;
  background: var(--border);
  margin: 1rem 0;
}

.references-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: var(--text-secondary);
  margin-bottom: 0.75rem;
}

.references-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.ref-tag {
  background: rgba(245, 158, 11, 0.1);
  border-color: rgba(245, 158, 11, 0.3);
  color: var(--primary);
  cursor: default;
}

.loading-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding: 2rem;
  color: var(--text-muted);
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 3px solid var(--border);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.loading-text {
  font-size: 0.875rem;
}

.chat-input-area {
  padding: 1.5rem 0;
  border-top: 1px solid var(--border);
}

.input-container {
  margin-bottom: 1rem;
}

.chat-input {
  cursor: text;
}

.send-btn {
  cursor: pointer;
}

.quick-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  justify-content: center;
}

.quick-tag {
  cursor: pointer;
  transition: all var(--transition-normal) ease;
}

.quick-tag:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(245, 158, 11, 0.2);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .chat-container {
    padding: 1rem;
  }

  .page-title {
    font-size: 1.5rem;
  }

  .message-avatar {
    width: 32px;
    height: 32px;
  }

  .message-content {
    padding: 0.75rem 1rem;
  }
}

@media (max-width: 375px) {
  .page-header {
    flex-direction: column;
    gap: 1rem;
    align-items: flex-start;
  }

  .quick-tags {
    gap: 0.25rem;
  }
}
</style>