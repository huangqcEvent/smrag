# 微交互与滚动体验优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 SMRAG 智能知识助手添加细腻的微交互动画和智能滚动体验，提升用户交互流畅度。

**Architecture:** 采用纯 CSS 动画方案，使用 Vue 3 Composition API 管理状态，遵循渐进增强原则，所有动画尊重 `prefers-reduced-motion` 偏好设置。

**Tech Stack:** Vue 3 Composition API, Element Plus, CSS Variables, CSS Animations

## Global Constraints

- 所有动画使用 CSS transform/opacity（GPU 加速）
- 动画时长统一：150-300ms（微交互），180-250ms（过渡）
- 缓动函数统一：`ease-out`（进入），`ease-in`（离开）
- 必须尊重 `prefers-reduced-motion` 媒体查询
- 无额外动画库依赖
- 使用 `passive: true` 监听滚动事件
- 使用 `requestAnimationFrame` 节流滚动处理

---

## Task 1: 全局滚动条美化

**Files:**
- Modify: `src/style.css`

**Interfaces:**
- Produces: 统一的滚动条样式，应用于所有页面

**目标：** 统一滚动条样式，与整体设计语言一致，减少视觉干扰。

- [ ] **Step 1: 添加 Webkit 滚动条样式**

在 `src/style.css` 文件末尾添加以下样式：

```css
/* ====== 自定义滚动条样式 ====== */

/* Webkit 内核浏览器（Chrome, Safari, Edge）*/
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  border-radius: 4px;
  background: color-mix(in srgb, var(--border-strong) 50%, transparent);
  transition: background 160ms ease-out;
}

::-webkit-scrollbar-thumb:hover {
  background: color-mix(in srgb, var(--border-strong) 70%, transparent);
}

/* Firefox */
* {
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--border-strong) 50%, transparent) transparent;
}
```

- [ ] **Step 2: 添加容器悬停时强调效果**

在同一位置继续添加：

```css
/* 悬停容器时滚动条更明显 */
.messages-container:hover::-webkit-scrollbar-thumb,
.knowledge-content:hover::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--border-strong) 65%, transparent);
}

/* 移动端优化：更细滚动条 */
@media (max-width: 768px) {
  ::-webkit-scrollbar {
    width: 4px;
    height: 4px;
  }
  
  ::-webkit-scrollbar-thumb {
    background: transparent;
  }
}

/* 尊重减少动画偏好 */
@media (prefers-reduced-motion: reduce) {
  ::-webkit-scrollbar-thumb {
    transition: none;
  }
}
```

- [ ] **Step 3: 启动开发服务器验证**

运行：
```bash
npm run dev
```

**验证要点：**
- 打开浏览器开发者工具，检查滚动条宽度是否为 8px
- 悬停在聊天或知识库内容区域，检查滚动条颜色是否加深
- 在移动端模拟器（宽度 < 768px）检查滚动条是否更细或透明

- [ ] **Step 4: 提交改动**

```bash
git add src/style.css
git commit -m "style: add custom scrollbar styling

- Add thin, semi-transparent scrollbar for webkit browsers
- Add Firefox scrollbar support
- Add hover emphasis effect
- Optimize for mobile (4px width)
- Respect prefers-reduced-motion

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 2: 骨架屏组件开发

**Files:**
- Create: `src/components/SkeletonTable.vue`
- Modify: `src/views/Knowledge.vue`

**Interfaces:**
- Produces: `<SkeletonTable>` 组件，接受 `rows` prop，显示 shimmer 加载动画

**目标：** 创建文档列表骨架屏组件，在数据加载时显示占位，减少视觉闪烁。

- [ ] **Step 1: 创建骨架屏组件文件**

创建文件 `src/components/SkeletonTable.vue`：

```vue
<template>
  <div class="skeleton-table">
    <div class="skeleton-row" v-for="i in rows" :key="i">
      <div class="skeleton-cell" style="width: 40%"></div>
      <div class="skeleton-cell" style="width: 15%"></div>
      <div class="skeleton-cell" style="width: 20%"></div>
      <div class="skeleton-cell" style="width: 10%"></div>
      <div class="skeleton-cell" style="width: 15%"></div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  rows: {
    type: Number,
    default: 5
  }
})
</script>

<style scoped>
.skeleton-table {
  padding: 12px 0;
}

.skeleton-row {
  display: flex;
  gap: 16px;
  padding: 14px 16px;
}

.skeleton-row + .skeleton-row {
  border-top: 1px solid var(--border-subtle);
}

.skeleton-cell {
  height: 16px;
  border-radius: 4px;
  background: linear-gradient(
    90deg,
    var(--surface-hover) 0%,
    var(--surface-raised) 50%,
    var(--surface-hover) 100%
  );
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}

@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-cell {
    animation: none;
    background: var(--surface-hover);
  }
}
</style>
```

- [ ] **Step 2: 在 Knowledge.vue 中导入并使用骨架屏**

修改 `src/views/Knowledge.vue`：

**在 script setup 部分添加导入：**
```vue
<script setup>
// ... 现有导入
import SkeletonTable from '@/components/SkeletonTable.vue'

// ... 其余代码
</script>
```

**在 template 部分，替换文档列表区域：**

找到 `<section class="document-workspace">` 部分，修改 `.table-scroll` 内容：

```vue
<div class="table-scroll">
  <SkeletonTable 
    v-if="loading && documents.length === 0" 
    :rows="6" 
  />
  <el-table
    v-else
    :data="documents"
    v-loading="loading && documents.length > 0"
    class="documents-table"
    empty-text="暂无文档"
  >
    <!-- 保持现有的 el-table-column 不变 -->
  </el-table>
</div>
```

- [ ] **Step 3: 测试骨架屏显示**

运行：
```bash
npm run dev
```

**验证要点：**
- 清空知识库 ID 输入框，点击刷新按钮
- 观察文档列表区域是否显示 6 行骨架屏
- 骨架屏是否有 shimmer 流动效果
- 数据加载完成后是否平滑过渡到真实表格

**测试场景：**
1. 首次进入知识库页面（无数据）→ 显示骨架屏
2. 已有数据时刷新 → 显示 Element Plus loading 遮罩
3. 系统偏好设置为"减少动画" → 骨架屏静止，无 shimmer

- [ ] **Step 4: 提交改动**

```bash
git add src/components/SkeletonTable.vue src/views/Knowledge.vue
git commit -m "feat: add skeleton loading for document table

- Create SkeletonTable component with shimmer effect
- Integrate skeleton in Knowledge.vue for initial load
- Use Element loading for subsequent refreshes
- Respect prefers-reduced-motion

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 3: 引用详情展开/收起过渡

**Files:**
- Modify: `src/views/Chat.vue`

**Interfaces:**
- Produces: 平滑的引用详情展开/收起动画

**目标：** 为引用详情添加平滑的展开收起过渡动画。

- [ ] **Step 1: 修改引用详情结构**

在 `src/views/Chat.vue` 的 template 中，找到引用部分（`<details class="citations">`），将 `<ol>` 包裹在 `<div class="citations-content">` 中：

```vue
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
```

- [ ] **Step 2: 添加过渡动画样式**

在 `Chat.vue` 的 `<style scoped>` 部分，找到 `.citations` 相关样式，修改为：

```vue
<style scoped>
/* ... 现有样式 ... */

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

/* 新增：引用内容过渡动画 */
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

/* ... 其余引用相关样式保持不变 ... */

@media (prefers-reduced-motion: reduce) {
  .citations-content {
    transition: none;
  }
}
</style>
```

- [ ] **Step 3: 测试引用展开动画**

运行：
```bash
npm run dev
```

**验证要点：**
- 发送一个问题，等待 AI 回复带引用的答案
- 点击"引用依据"标题，观察内容是否平滑展开（180ms）
- 再次点击，观察内容是否平滑收起
- 键盘导航到引用详情，按 Enter 键测试展开/收起

**测试场景：**
1. 点击展开 → 内容从 0 高度平滑展开
2. 点击收起 → 内容平滑收起到 0
3. 系统偏好"减少动画" → 无动画，立即显示/隐藏

- [ ] **Step 4: 提交改动**

```bash
git add src/views/Chat.vue
git commit -m "feat: add smooth citations expand/collapse transition

- Wrap citations content in transition container
- Add max-height + opacity transition (180ms)
- Preserve keyboard navigation support
- Respect prefers-reduced-motion

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 4: 智能自动滚动逻辑

**Files:**
- Modify: `src/views/Chat.vue`

**Interfaces:**
- Consumes: 现有的 `messagesContainer`, `chatHistory`, `loading`
- Produces: `isNearBottom`, `showScrollButton` 状态，`handleScroll`, `scrollToBottom` 方法

**目标：** 实现智能自动滚动，用户向上浏览时暂停自动滚动。

- [ ] **Step 1: 添加滚动状态管理**

在 `Chat.vue` 的 `<script setup>` 中，添加滚动相关状态和方法：

```vue
<script setup>
// ... 现有导入
import { ArrowDown } from '@element-plus/icons-vue'

// ... 现有代码

// 滚动状态管理
const isNearBottom = ref(true)
const showScrollButton = ref(false)

// 监听滚动位置
const handleScroll = () => {
  const container = messagesContainer.value
  if (!container) return
  
  const threshold = 150
  const distanceFromBottom = 
    container.scrollHeight - container.scrollTop - container.clientHeight
  
  isNearBottom.value = distanceFromBottom <= threshold
  showScrollButton.value = distanceFromBottom > threshold
}

// 滚动到底部
const scrollToBottom = (smooth = true) => {
  const container = messagesContainer.value
  if (!container) return
  
  container.scrollTo({
    top: container.scrollHeight,
    behavior: smooth && !prefersReducedMotion.matches ? 'smooth' : 'auto'
  })
  showScrollButton.value = false
}

// 生命周期：添加滚动监听
onMounted(() => {
  chatStore.initSession()
  messagesContainer.value?.addEventListener('scroll', handleScroll, { passive: true })
})

onBeforeUnmount(() => {
  if (scrollFrame) cancelAnimationFrame(scrollFrame)
  messagesContainer.value?.removeEventListener('scroll', handleScroll)
  chatStore.stopGeneration()
})

// 修改现有的 watch：仅在用户位于底部时自动滚动
watch(
  () => {
    const last = chatHistory.value.at(-1)
    return [chatHistory.value.length, last?.answer?.length, last?.state, last?.citations?.length]
  },
  () => {
    if (isNearBottom.value) {
      scheduleScrollToBottom()
    }
  },
  { flush: 'post' }
)

// ... 其余代码
</script>
```

- [ ] **Step 2: 测试智能滚动逻辑**

运行：
```bash
npm run dev
```

**验证要点：**
1. **自动跟随：** 发送消息，观察是否自动滚动到新消息
2. **暂停跟随：** 向上滚动超过 150px，发送新消息，观察是否不自动滚动
3. **恢复跟随：** 滚动回底部，发送新消息，观察是否恢复自动滚动

**测试场景：**
- 连续发送 3 条消息，验证每次都自动滚动到底部
- 向上滚动查看历史，发送新消息，验证停留在当前位置
- 滚动回底部，发送新消息，验证恢复自动跟随

- [ ] **Step 3: 提交改动**

```bash
git add src/views/Chat.vue
git commit -m "feat: add smart auto-scroll logic

- Track user scroll position with 150px threshold
- Pause auto-scroll when user scrolls up
- Use passive scroll listener for performance
- Resume auto-scroll when user scrolls to bottom
- Prepare scroll-to-bottom button state

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 5: 滚动到底部浮动按钮

**Files:**
- Modify: `src/views/Chat.vue`

**Interfaces:**
- Consumes: `showScrollButton` 状态，`scrollToBottom` 方法
- Produces: 可见的浮动按钮，点击滚动到底部

**目标：** 添加浮动按钮，让用户快速回到最新消息。

- [ ] **Step 1: 在 template 中添加按钮**

在 `Chat.vue` 的 `<section class="chat-page">` 末尾，`.composer-dock` 之后添加：

```vue
<template>
  <section class="chat-page">
    <!-- 现有内容：header, messages-container, composer-dock -->
    
    <!-- 滚动到底部按钮 -->
    <transition name="scroll-button">
      <button
        v-if="showScrollButton"
        class="scroll-to-bottom"
        type="button"
        aria-label="滚动到最新消息"
        @click="scrollToBottom(true)"
      >
        <el-icon><ArrowDown /></el-icon>
      </button>
    </transition>
  </section>
</template>
```

- [ ] **Step 2: 添加按钮样式**

在 `Chat.vue` 的 `<style scoped>` 末尾添加：

```vue
<style scoped>
/* ... 现有样式 ... */

/* ====== 滚动到底部按钮 ====== */

.scroll-to-bottom {
  position: fixed;
  right: 24px;
  bottom: 90px;
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 50%;
  background: var(--accent);
  color: var(--accent-contrast);
  font-size: 1.25rem;
  cursor: pointer;
  box-shadow: var(--shadow-low);
  transition: transform 160ms ease-out, box-shadow 160ms ease-out;
  z-index: 10;
}

.scroll-to-bottom:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(28, 48, 43, 0.12);
}

.scroll-to-bottom:active {
  transform: scale(0.95);
}

/* 按钮进入/离开动画 */
.scroll-button-enter-active,
.scroll-button-leave-active {
  transition: opacity 200ms ease-out, transform 200ms ease-out;
}

.scroll-button-enter-from,
.scroll-button-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 640px) {
  .scroll-to-bottom {
    right: 16px;
    bottom: 100px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .scroll-to-bottom {
    transition: none;
  }
  
  .scroll-button-enter-active,
  .scroll-button-leave-active {
    transition: opacity 100ms;
  }
  
  .scroll-button-enter-from,
  .scroll-button-leave-to {
    transform: none;
  }
}
</style>
```

- [ ] **Step 3: 测试按钮显示与交互**

运行：
```bash
npm run dev
```

**验证要点：**
1. **显示触发：** 向上滚动超过 150px，验证按钮从右下角淡入
2. **点击效果：** 点击按钮，验证平滑滚动到底部，按钮淡出消失
3. **悬停反馈：** 悬停按钮，验证轻微上浮（-2px）
4. **点击反馈：** 点击时验证缩放（scale 0.95）

**测试场景：**
- 发送多条消息，向上滚动查看历史，验证按钮出现
- 点击按钮，验证滚动到底部且按钮消失
- 移动端模拟器（宽度 < 640px），验证按钮位置调整

- [ ] **Step 4: 提交改动**

```bash
git add src/views/Chat.vue
git commit -m "feat: add scroll-to-bottom floating button

- Add fixed circular button (44x44px)
- Fade in/out transition with slide-up effect
- Smooth scroll on click
- Hover lift and active scale feedback
- Mobile-optimized positioning
- Respect prefers-reduced-motion

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 6: 消息进入动画

**Files:**
- Modify: `src/views/Chat.vue`

**Interfaces:**
- Produces: 新消息的淡入上浮动画（200ms）

**目标：** 为新消息添加平滑的进入动画。

- [ ] **Step 1: 添加消息进入动画类**

在 `Chat.vue` 的 `<style scoped>` 中添加：

```vue
<style scoped>
/* ... 现有样式 ... */

/* ====== 消息进入动画 ====== */

.message-enter {
  animation: message-slide-in 200ms ease-out;
}

@keyframes message-slide-in {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .message-enter {
    animation: none;
  }
}
</style>
```

- [ ] **Step 2: 在 template 中应用动画类**

修改 `Chat.vue` 的 `<article>` 元素：

```vue
<article
  v-for="(item, index) in chatHistory"
  :key="item.sessionId + '-' + index"
  class="message-pair"
  :class="{ 'message-enter': isRecentMessage(index) }"
>
  <!-- 消息内容保持不变 -->
</article>
```

- [ ] **Step 3: 添加判断新消息的逻辑**

在 `Chat.vue` 的 `<script setup>` 中添加：

```vue
<script setup>
// ... 现有代码

// 判断是否为"新"消息（最近500ms内添加）
const isRecentMessage = (index) => {
  const item = chatHistory.value[index]
  if (!item.createTime) return false
  const elapsed = Date.now() - new Date(item.createTime).getTime()
  return elapsed < 500 && index === chatHistory.value.length - 1
}

// ... 其余代码
</script>
```

- [ ] **Step 4: 测试消息进入动画**

运行：
```bash
npm run dev
```

**验证要点：**
1. **用户消息：** 发送问题后，用户消息从底部淡入上浮（16px，200ms）
2. **AI 回复：** AI 回复整体滑入，然后流式内容逐步显示
3. **历史消息：** 刷新页面后，历史消息无动画直接显示

**测试场景：**
- 发送单条消息，观察动画效果
- 快速连续发送 3 条消息，验证每条都有动画
- 刷新页面，验证历史消息无动画
- 系统偏好"减少动画"，验证无动画立即显示

- [ ] **Step 5: 提交改动**

```bash
git add src/views/Chat.vue
git commit -m "feat: add message entrance animation

- Add fade-in slide-up animation for new messages
- 200ms duration with ease-out timing
- Apply only to recent messages (< 500ms old)
- GPU-accelerated transform/opacity
- Respect prefers-reduced-motion

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 7: 页面切换过渡增强

**Files:**
- Modify: `src/App.vue`
- Modify: `src/router/index.js`

**Interfaces:**
- Produces: 有方向感的页面切换滑动动画

**目标：** 为聊天 ↔ 知识库切换添加方向性的滑动过渡。

- [ ] **Step 1: 修改 App.vue 过渡样式**

在 `src/App.vue` 的 `<style scoped>` 中，将现有的 `page-fade` 过渡改为 `page-slide`：

```vue
<style scoped>
/* ... 现有样式 ... */

/* ====== 页面切换过渡 ====== */

.page-slide-enter-active,
.page-slide-leave-active {
  transition: opacity 200ms ease-out, transform 200ms ease-out;
}

.page-slide-enter-from {
  opacity: 0;
  transform: translateX(24px);
}

.page-slide-leave-to {
  opacity: 0;
  transform: translateX(-24px);
}

/* 反向动画（知识库 → 聊天）*/
.page-slide-reverse-enter-from {
  opacity: 0;
  transform: translateX(-24px);
}

.page-slide-reverse-leave-to {
  opacity: 0;
  transform: translateX(24px);
}

@media (prefers-reduced-motion: reduce) {
  .page-slide-enter-active,
  .page-slide-leave-active {
    transition: opacity 120ms;
  }
  
  .page-slide-enter-from,
  .page-slide-leave-to {
    transform: none;
  }
}
</style>
```

- [ ] **Step 2: 修改 App.vue 的 transition 组件**

修改 `<router-view>` 中的 `<transition>` 组件：

```vue
<router-view v-slot="{ Component, route }">
  <transition 
    :name="route.meta.transition || 'page-slide'"
    mode="out-in"
  >
    <component :is="Component" :key="route.path" />
  </transition>
</router-view>
```

- [ ] **Step 3: 修改路由配置添加过渡元信息**

修改 `src/router/index.js`：

```javascript
const routes = [
  {
    path: '/',
    redirect: '/chat'
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/Chat.vue'),
    meta: { 
      title: '智能问答',
      transition: 'page-slide'
    }
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('@/views/Knowledge.vue'),
    meta: { 
      title: '知识库管理',
      transition: 'page-slide-reverse'
    }
  }
]
```

- [ ] **Step 4: 测试页面切换动画**

运行：
```bash
npm run dev
```

**验证要点：**
1. **聊天 → 知识库：** 当前页向左滑出（-24px），新页从右侧滑入（+24px）
2. **知识库 → 聊天：** 当前页向右滑出（+24px），新页从左侧滑入（-24px）
3. **动画时长：** 200ms + 淡入淡出
4. **无障碍：** 系统偏好"减少动画"时，仅淡入淡出

**测试场景：**
- 从聊天页点击"知识库"，验证滑动方向
- 从知识库页点击"智能问答"，验证反向滑动
- 快速切换 5 次，验证动画流畅无卡顿
- 移动端测试，验证滑动效果

- [ ] **Step 5: 提交改动**

```bash
git add src/App.vue src/router/index.js
git commit -m "feat: add directional page transition

- Replace fade with slide transition (200ms)
- Chat → Knowledge: slide left
- Knowledge → Chat: slide right (reverse)
- Use route meta for transition name
- Respect prefers-reduced-motion (fade only)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Task 8: 最终集成测试与文档更新

**Files:**
- Modify: `README.md` (可选)

**目标：** 全面测试所有功能，确保无遗漏和错误。

- [ ] **Step 1: 功能完整性测试清单**

按以下清单逐项测试：

**微交互：**
- [ ] 消息进入动画正常（淡入上浮，200ms）
- [ ] 骨架屏显示正确（首次加载，shimmer 效果）
- [ ] 引用详情展开/收起平滑（180ms）

**滚动体验：**
- [ ] 自动滚动智能暂停（用户向上滚动）
- [ ] 滚动到底部按钮显示正确（距底部 > 150px）
- [ ] 点击按钮平滑滚动到底部
- [ ] 全局滚动条样式一致（8px，半透明）

**页面切换：**
- [ ] 聊天 ↔ 知识库滑动过渡正确
- [ ] 方向感清晰（左滑/右滑）

**无障碍：**
- [ ] 所有动画在"减少动画"模式下禁用
- [ ] 键盘导航正常（Tab，Enter）
- [ ] 滚动按钮有 aria-label

- [ ] **Step 2: 性能测试**

**测试命令：**
```bash
npm run build
npm run preview
```

**验证要点：**
- 打包无错误
- 生产版本体积合理（增加 < 50KB）
- 浏览器开发者工具 Performance 录制，动画帧率 ≥ 55fps

- [ ] **Step 3: 跨浏览器测试**

**测试矩阵：**
- [ ] Chrome（最新版）
- [ ] Firefox（最新版）
- [ ] Safari（如可访问）
- [ ] Edge（最新版）
- [ ] 移动端 Chrome DevTools 模拟

**重点验证：**
- Firefox 滚动条样式是否生效（`scrollbar-width: thin`）
- Safari 滚动条是否降级为默认样式（可接受）
- 移动端滚动条是否更细（4px）

- [ ] **Step 4: 更新项目文档**

可选：在 `README.md` 中添加新功能说明：

```markdown
## 功能特性

### 微交互与动画
- ✨ 消息进入动画：新消息淡入上浮效果
- 🎨 骨架屏加载：文档列表占位动画
- 📜 引用详情过渡：平滑展开收起

### 滚动体验
- 🎯 智能自动滚动：用户向上浏览时暂停
- ⬇️ 滚动到底部按钮：快速返回最新消息
- 🖱️ 自定义滚动条：统一视觉风格

### 页面切换
- 🔄 方向性滑动过渡：聊天 ↔ 知识库切换动画

### 无障碍
- ♿ 尊重系统动画偏好设置
- ⌨️ 完整键盘导航支持
```

- [ ] **Step 5: 最终提交**

```bash
git add -A
git commit -m "test: complete micro-interactions integration testing

All features tested and verified:
- Message entrance animations
- Skeleton loading states
- Citations expand/collapse
- Smart auto-scroll logic
- Scroll-to-bottom button
- Custom scrollbar styling
- Page transition effects

Cross-browser compatibility verified.
Accessibility requirements met.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 验收标准

完成后，项目应满足以下标准：

✅ **功能完整**
- 所有设计文档中的功能均已实现
- 无遗漏的功能点

✅ **性能达标**
- 动画帧率 ≥ 55fps
- 打包体积增加 < 50KB
- 无内存泄漏

✅ **无障碍友好**
- 所有动画可被系统偏好禁用
- 键盘导航完整
- ARIA 标签正确

✅ **跨浏览器兼容**
- Chrome/Firefox/Edge 完全支持
- Safari 优雅降级
- 移动端适配良好

✅ **代码质量**
- 遵循现有代码风格
- 注释清晰
- Git 提交规范

---

## 回滚计划

如遇严重问题，按以下步骤回滚：

**回滚单个功能：**
```bash
git revert <commit-hash>
```

**完整回滚：**
```bash
git reset --hard 83d654a  # 设计文档提交
```

**关键提交点：**
- Task 1: 全局滚动条样式
- Task 2: 骨架屏组件
- Task 3: 引用过渡
- Task 4: 智能滚动逻辑
- Task 5: 滚动到底部按钮
- Task 6: 消息进入动画
- Task 7: 页面切换过渡

每个任务都可独立回滚，不影响其他功能。