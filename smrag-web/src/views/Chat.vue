<template>
  <div class="chat-page">
    <!-- 页面头部 -->
    <div class="chat-header">
      <div class="header-left">
        <div class="header-icon">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
          </svg>
        </div>
        <div class="header-info">
          <h1 class="header-title">智能问答</h1>
          <p class="header-subtitle">AI驱动的私募基金智能助手</p>
        </div>
      </div>
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

    <!-- 聊天区域 -->
    <div class="chat-body">
      <!-- 消息列表 -->
      <div class="messages-container" ref="messagesContainer">
        <div v-if="chatHistory.length === 0" class="empty-state">
          <div class="empty-visual">
            <div class="empty-circle">
              <svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <circle cx="12" cy="12" r="10"/>
                <path d="M8 14s1.5 2 4 2 4-2 4-2"/>
                <line x1="9" y1="9" x2="9.01" y2="9"/>
                <line x1="15" y1="9" x2="15.01" y2="9"/>
              </svg>
            </div>
          </div>
          <div class="empty-content">
            <h3 class="empty-title">开始您的智能问答之旅</h3>
            <p class="empty-desc">向AI专家提问，获取专业的私募基金建议和分析</p>
          </div>
        </div>

        <div v-for="(item, index) in chatHistory" :key="index" class="message-wrapper">
          <!-- 用户消息 -->
          <div class="user-bubble">
            <div class="bubble-header">
              <div class="user-badge">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
                您
              </div>
              <div class="message-time">{{ formatTime(index) }}</div>
            </div>
            <div class="bubble-content user-content">
              {{ item.question }}
            </div>
          </div>

          <!-- AI回复 -->
          <div class="ai-bubble">
            <div class="bubble-header">
              <div class="ai-badge">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
                </svg>
                AI专家
              </div>
              <el-tag size="small" :type="getRouteTagType(item.routeType)" class="route-badge">
                {{ getRouteLabel(item.routeType) }}
              </el-tag>
            </div>

            <div class="bubble-content ai-content">
              <div class="ai-text">{{ item.answer }}</div>

              <!-- 参考资料 -->
              <div v-if="item.references && item.references.length > 0" class="references-box">
                <div class="ref-header">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                    <polyline points="14 2 14 8 20 8"/>
                    <line x1="16" y1="13" x2="8" y2="13"/>
                    <line x1="16" y1="17" x2="8" y2="17"/>
                  </svg>
                  参考来源
                </div>
                <div class="ref-list">
                  <div v-for="(ref, i) in item.references" :key="i" class="ref-item">
                    <span class="ref-number">{{ i + 1 }}</span>
                    <span class="ref-text">{{ ref }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 加载状态 -->
        <div v-if="loading" class="loading-bubble">
          <div class="loading-animation">
            <div class="loading-dot"></div>
            <div class="loading-dot"></div>
            <div class="loading-dot"></div>
          </div>
          <span class="loading-label">AI正在分析...</span>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="input-section">
        <div class="input-box">
          <el-input
            v-model="question"
            placeholder="输入问题，例如：什么是量化对冲策略？"
            @keyup.enter="handleSend"
            :disabled="loading"
            size="large"
            class="chat-input"
          >
            <template #suffix>
              <el-button
                @click="handleSend"
                :loading="loading"
                type="primary"
                circle
                class="send-button"
              >
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="22" y1="2" x2="11" y2="13"/>
                  <polygon points="22 2 15 22 11 13 2 9 22 2"/>
                </svg>
              </el-button>
            </template>
          </el-input>
        </div>

        <!-- 快捷提问 -->
        <div class="quick-actions">
          <div class="quick-item" @click="quickAsk('什么是量化对冲策略？')">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
              <line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
            知识问答
          </div>
          <div class="quick-item" @click="quickAsk('查询高风险产品')">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/>
              <line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
            产品查询
          </div>
          <div class="quick-item" @click="quickAsk('对比两只基金的收益率')">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="20" x2="18" y2="10"/>
              <line x1="12" y1="20" x2="12" y2="4"/>
              <line x1="6" y1="20" x2="6" y2="14"/>
            </svg>
            产品对比
          </div>
          <div class="quick-item" @click="quickAsk('推荐适合新手的产品')">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
            </svg>
            产品推荐
          </div>
          <div class="quick-item" @click="quickAsk('生成私募基金销售话术')">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
            销售话术
          </div>
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

const formatTime = (index) => {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
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
  height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, var(--bg-primary) 0%, #1a2332 100%);
  overflow: hidden;
}

.chat-header {
  padding: 2rem 3rem;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.header-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, var(--primary), var(--cta));
  border-radius: 50%;
  color: var(--text-primary);
  animation: glow 2s ease-in-out infinite alternate;
}

@keyframes glow {
  from {
    box-shadow: 0 0 20px rgba(245, 158, 11, 0.5);
  }
  to {
    box-shadow: 0 0 30px rgba(139, 92, 246, 0.8);
  }
}

.header-info {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.header-title {
  font-family: var(--font-code);
  font-size: 2rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.header-subtitle {
  font-size: 0.875rem;
  color: var(--text-muted);
  margin: 0;
}

.clear-btn {
  cursor: pointer;
}

.chat-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  max-width: 1000px;
  margin: 0 auto;
  width: 100%;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 2rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  text-align: center;
}

.empty-visual {
  margin-bottom: 2rem;
}

.empty-circle {
  position: relative;
  animation: float 3s ease-in-out infinite;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0px);
  }
  50% {
    transform: translateY(-20px);
  }
}

.empty-content {
  max-width: 400px;
}

.empty-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 0.75rem;
}

.empty-desc {
  font-size: 1rem;
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.6;
}

.message-wrapper {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.user-bubble, .ai-bubble {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.bubble-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.user-badge, .ai-badge {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.25rem 0.75rem;
  border-radius: var(--radius-md);
  font-size: 0.75rem;
  font-weight: 500;
}

.user-badge {
  background: rgba(100, 116, 139, 0.2);
  color: var(--text-secondary);
}

.ai-badge {
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.2), rgba(139, 92, 246, 0.2));
  color: var(--primary);
}

.message-time {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.route-badge {
  cursor: default;
}

.bubble-content {
  padding: 1rem 1.25rem;
  border-radius: var(--radius-lg);
  line-height: 1.6;
}

.user-content {
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  color: var(--text-primary);
  margin-left: auto;
  max-width: 70%;
}

.ai-content {
  background: linear-gradient(135deg, rgba(30, 41, 59, 0.8), rgba(30, 41, 59, 0.6));
  border: 1px solid rgba(245, 158, 11, 0.2);
  color: var(--text-primary);
  max-width: 80%;
}

.ai-text {
  font-size: 1rem;
}

.references-box {
  margin-top: 1rem;
  padding: 1rem;
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(139, 92, 246, 0.2);
  border-radius: var(--radius-md);
}

.ref-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: var(--cta);
  margin-bottom: 0.75rem;
  font-weight: 500;
}

.ref-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.ref-item {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  font-size: 0.875rem;
}

.ref-number {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  background: rgba(139, 92, 246, 0.2);
  border-radius: 50%;
  color: var(--cta);
  font-weight: 600;
  font-size: 0.75rem;
}

.ref-text {
  color: var(--text-secondary);
  line-height: 1.5;
}

.loading-bubble {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem 1.5rem;
  background: rgba(30, 41, 59, 0.6);
  border: 1px solid rgba(245, 158, 11, 0.2);
  border-radius: var(--radius-lg);
}

.loading-animation {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.loading-dot {
  width: 8px;
  height: 8px;
  background: var(--primary);
  border-radius: 50%;
  animation: bounce 1.4s ease-in-out infinite;
}

.loading-dot:nth-child(1) {
  animation-delay: 0s;
}

.loading-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.loading-dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

.loading-label {
  font-size: 0.875rem;
  color: var(--text-secondary);
}

.input-section {
  padding: 1.5rem 2rem 2rem;
  background: var(--bg-secondary);
  border-top: 1px solid var(--border);
}

.input-box {
  margin-bottom: 1rem;
}

.chat-input {
  cursor: text;
}

.send-button {
  cursor: pointer;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  justify-content: center;
}

.quick-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 0.875rem;
  cursor: pointer;
  transition: all var(--transition-normal) ease;
}

.quick-item:hover {
  background: rgba(245, 158, 11, 0.15);
  border-color: var(--primary);
  color: var(--primary);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(245, 158, 11, 0.3);
}

.quick-item svg {
  opacity: 0.7;
}

.quick-item:hover svg {
  opacity: 1;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .chat-header {
    padding: 1.5rem;
  }

  .header-icon {
    width: 48px;
    height: 48px;
  }

  .header-title {
    font-size: 1.5rem;
  }

  .messages-container {
    padding: 1rem;
  }

  .user-content, .ai-content {
    max-width: 90%;
  }

  .input-section {
    padding: 1rem;
  }

  .quick-actions {
    gap: 0.5rem;
  }

  .quick-item {
    font-size: 0.75rem;
    padding: 0.375rem 0.75rem;
  }
}

@media (max-width: 375px) {
  .header-icon {
    width: 40px;
    height: 40px;
  }

  .header-title {
    font-size: 1.25rem;
  }

  .quick-actions {
    flex-direction: column;
  }

  .quick-item {
    width: 100%;
    justify-content: center;
  }
}
</style>