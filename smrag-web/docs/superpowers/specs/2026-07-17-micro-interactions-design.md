# 微交互与滚动体验优化设计文档

**项目：** SMRAG 智能知识助手  
**日期：** 2026-07-17  
**版本：** 1.0  
**状态：** 已批准

---

## 1. 概述

### 1.1 项目背景

SMRAG 智能知识助手是一个基于 Vue 3 + Element Plus 的对话式 AI 应用，包含智能问答和知识库管理两个核心页面。当前实现已有良好的视觉基础，但在微交互与滚动体验方面仍有优化空间。

### 1.2 设计目标

**核心目标：** 提升交互流畅度与视觉反馈，让界面响应更细腻、滚动体验更智能。

**具体目标：**

1. **微交互**：
   - 消息进入时有平滑的淡入上浮动画
   - 列表加载时显示骨架屏，减少视觉闪烁
   - 引用详情展开/收起有平滑过渡

2. **滚动体验**：
   - 聊天消息自动跟随，但用户向上浏览时暂停
   - 提供悬浮"滚动到底部"按钮，快速回到最新消息
   - 统一滚动条样式，与整体设计语言一致

3. **性能与无障碍**：
   - 所有动画使用 CSS transform/opacity（GPU 加速）
   - 尊重用户的 `prefers-reduced-motion` 偏好
   - 保持现有 60fps 性能水平

### 1.3 设计原则

- **渐进增强**：在现有基础上润色，不改变整体设计气质
- **性能优先**：仅使用 CSS 动画，避免 JavaScript 计算布局
- **无障碍友好**：所有动效可被系统偏好禁用
- **一致性**：统一动画时长（150-300ms）和缓动函数

### 1.4 技术栈

- Vue 3 Composition API
- Element Plus 组件库
- CSS Variables + scoped styles
- 无额外动画库依赖

---

## 2. 微交互设计

### 2.1 消息进入动画

**设计目标：** 新消息从底部淡入上浮，营造自然流畅的视觉节奏。

**动画规格：**
- **初始状态：** `opacity: 0`, `transform: translateY(16px)`
- **结束状态：** `opacity: 1`, `transform: translateY(0)`
- **时长：** 200ms
- **缓动函数：** `ease-out`（进入时使用 ease-out 更自然）
- **触发时机：** 新消息渲染到 DOM 时

**实现要点：**

```vue
<!-- Chat.vue -->
<template>
  <article
    v-for="(item, index) in chatHistory"
    :key="item.sessionId + '-' + index"
    class="message-pair"
    :class="{ 'message-enter': isRecentMessage(index) }"
  >
    <!-- 消息内容 -->
  </article>
</template>

<style scoped>
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

**JS 逻辑：**
```javascript
// 判断是否为"新"消息（最近500ms内添加）
const isRecentMessage = (index) => {
  const item = chatHistory.value[index]
  if (!item.createTime) return false
  const elapsed = Date.now() - new Date(item.createTime).getTime()
  return elapsed < 500 && index === chatHistory.value.length - 1
}
```

**效果：**
- 用户发送问题后，用户消息和 AI 回复依次平滑滑入
- 流式输出时，AI 消息块整体滑入后，内容逐步显示
- 禁用动画时直接显示，无延迟

---

### 2.2 骨架屏加载态

**设计目标：** 文档列表加载时显示占位骨架，避免空白闪烁。

**应用场景：**
- Knowledge.vue：文档列表首次加载
- Chat.vue：（可选）历史消息加载

**动画规格：**
- **骨架样式：** 背景色 `var(--surface-hover)`，脉冲闪烁
- **shimmer 效果：** 从左到右的渐变光带，循环播放
- **时长：** 1.5s 循环

**实现要点：**

```vue
<!-- src/components/SkeletonTable.vue -->
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
  rows: { type: Number, default: 5 }
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
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-cell {
    animation: none;
    background: var(--surface-hover);
  }
}
</style>
```

**Knowledge.vue 集成：**
```vue
<template>
  <section class="document-workspace">
    <div class="table-scroll">
      <SkeletonTable v-if="loading && documents.length === 0" :rows="6" />
      <el-table v-else :data="documents" v-loading="loading && documents.length > 0">
        <!-- 表格列 -->
      </el-table>
    </div>
  </section>
</template>
```

**效果：**
- 首次加载显示 6 行骨架，shimmer 流动效果
- 数据到达后平滑过渡到真实表格
- 后续刷新使用 Element Plus 内置 loading 遮罩

---

### 2.3 引用详情展开/收起

**设计目标：** 点击引用标题时，内容区域平滑展开收起。

**动画规格：**
- **属性：** `max-height` + `opacity`
- **时长：** 180ms
- **缓动函数：** `ease-out`

**实现要点：**

```vue
<!-- Chat.vue 引用部分 -->
<template>
  <details class="citations">
    <summary>
      <span><el-icon><Document /></el-icon>引用依据</span>
      <span class="citation-count">{{ item.citations.length }}</span>
    </summary>
    <div class="citations-content">
      <ol>
        <!-- 引用列表 -->
      </ol>
    </div>
  </details>
</template>

<style scoped>
.citations-content {
  max-height: 0;
  opacity: 0;
  overflow: hidden;
  transition: max-height 180ms ease-out, opacity 180ms ease-out;
}

.citations[open] .citations-content {
  max-height: 1000px; /* 足够大的值 */
  opacity: 1;
}

.citations summary {
  cursor: pointer;
}

@media (prefers-reduced-motion: reduce) {
  .citations-content {
    transition: none;
  }
}
</style>
```

**效果：**
- 点击"引用依据"标题，内容从 0 高度平滑展开
- 收起时反向动画
- 无障碍友好，保持 `<details>` 的原生键盘支持

---

## 3. 滚动体验优化

### 3.1 智能自动滚动逻辑

**设计目标：** 聊天消息自动跟随，但当用户向上浏览历史时暂停，避免打断阅读。

**核心逻辑：**

| 场景 | 行为 |
|------|------|
| 用户在底部（距底部 ≤ 150px） | 新消息到达时自动滚动到底部 |
| 用户向上滚动（距底部 > 150px） | 暂停自动滚动，显示"回到底部"按钮 |
| 用户点击"回到底部" | 立即滚动到底部，恢复自动跟随 |
| 流式输出中 | 保持当前行为（已在底部时跟随） |

**实现要点：**

```javascript
// Chat.vue 状态管理
const isNearBottom = ref(true)       // 用户是否在底部区域
const showScrollButton = ref(false)  // 是否显示"回到底部"按钮

// 监听滚动位置
const handleScroll = () => {
  const container = messagesContainer.value
  if (!container) return
  
  const threshold = 150
  const distanceFromBottom = container.scrollHeight - container.scrollTop - container.clientHeight
  
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
}

// 生命周期集成
onMounted(() => {
  chatStore.initSession()
  messagesContainer.value?.addEventListener('scroll', handleScroll, { passive: true })
})

onBeforeUnmount(() => {
  messagesContainer.value?.removeEventListener('scroll', handleScroll)
})

// 监听消息变化
watch(
  () => {
    const last = chatHistory.value.at(-1)
    return [chatHistory.value.length, last?.answer?.length]
  },
  () => {
    if (isNearBottom.value) {
      scheduleScrollToBottom()
    }
  },
  { flush: 'post' }
)
```

**效果：**
- 用户浏览最新消息时，新消息自动推入视野
- 用户查看历史时，不会被打断
- 明确的视觉反馈（按钮）告知如何返回

---

### 3.2 "滚动到底部"浮动按钮

**设计目标：** 提供快速回到最新消息的入口，位置固定，不遮挡内容。

**视觉规格：**
- **位置：** 右下角，距离底部 80px（避免与输入框重叠）
- **尺寸：** 44x44px（满足最小触控尺寸）
- **样式：** 圆形，primary 色背景，白色图标
- **动画：** 淡入淡出 + 轻微上浮

**实现要点：**

```vue
<!-- Chat.vue -->
<template>
  <section class="chat-page">
    <!-- 现有内容 -->
    
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

<style scoped>
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

**图标导入：**
```javascript
import { ArrowDown } from '@element-plus/icons-vue'
```

**效果：**
- 用户向上滚动超过 150px 时，按钮从右下角淡入
- 点击后平滑滚动到底部并隐藏按钮
- 悬停时轻微上浮，点击时缩放反馈

---

### 3.3 全局滚动条美化

**设计目标：** 统一滚动条样式，与整体设计语言一致，减少视觉干扰。

**视觉规格：**
- **宽度：** 8px（默认）→ 6px（悬停时保持 6px）
- **颜色：** `var(--border-strong)` 半透明
- **悬停：** 颜色加深
- **圆角：** 4px

**实现要点：**

```css
/* style.css 添加全局样式 */

/* 自定义滚动条 - Webkit 内核浏览器 */
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

/* 强调型滚动条（悬停容器时更明显）*/
.messages-container:hover::-webkit-scrollbar-thumb,
.knowledge-content:hover::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--border-strong) 65%, transparent);
}

/* 移动端隐藏滚动条（保持滑动） */
@media (max-width: 768px) {
  ::-webkit-scrollbar {
    width: 4px;
    height: 4px;
  }
  
  ::-webkit-scrollbar-thumb {
    background: transparent;
  }
}
```

**效果：**
- 滚动条细窄、半透明，不抢占视觉焦点
- 悬停时颜色加深，提示用户
- 移动端几乎隐藏，保持原生滑动体验
- 跨浏览器支持

---

### 3.4 页面切换过渡增强

**设计目标：** 聊天 ↔ 知识库切换时，增加轻微滑动效果，增强空间感知。

**动画规格：**
- **聊天 → 知识库：** 当前页向左滑出，新页从右侧滑入
- **知识库 → 聊天：** 当前页向右滑出，新页从左侧滑入
- **时长：** 250ms（比默认稍长，更明显）
- **缓动函数：** `ease-out`

**实现要点：**

```vue
<!-- App.vue -->
<template>
  <div class="app-shell">
    <!-- header -->
    
    <main class="app-main">
      <router-view v-slot="{ Component, route }">
        <transition 
          :name="route.meta.transition || 'page-slide'"
          mode="out-in"
        >
          <component :is="Component" :key="route.path" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<style scoped>
/* 现有 page-fade 改为 page-slide */

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

**路由配置：**
```javascript
// router/index.js
const routes = [
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

**效果：**
- 切换页面时有方向感的滑动，而非简单的淡入淡出
- 用户感知页面间的空间关系
- 禁用动画时仍保持基础淡入淡出

---

## 4. 技术架构

### 4.1 文件修改清单

#### 新建文件

| 文件路径 | 用途 | 代码量 |
|---------|------|--------|
| `src/components/SkeletonTable.vue` | 文档列表骨架屏组件 | ~60 行 |

#### 修改文件

| 文件路径 | 改动内容 | 改动量 |
|---------|---------|--------|
| `src/views/Chat.vue` | 消息进入动画、智能滚动、滚动到底部按钮 | +120 行 |
| `src/views/Knowledge.vue` | 骨架屏集成 | +10 行 |
| `src/style.css` | 全局滚动条样式 | +50 行 |
| `src/App.vue` | 页面切换过渡增强 | +40 行 |
| `src/router/index.js` | 路由过渡元信息 | +4 行 |

**总计：** 新增 ~1 个文件，修改 ~5 个文件，净增 ~230 行代码。

---

### 4.2 实施顺序

**Phase 1：基础动效（低风险）**
1. 全局滚动条样式
2. 骨架屏组件开发与集成
3. 引用详情展开/收起过渡

**Phase 2：滚动体验（中风险）**
4. 智能自动滚动逻辑（Chat.vue）
5. 滚动到底部按钮（Chat.vue）

**Phase 3：高级动效（需测试）**
6. 消息进入动画（Chat.vue）
7. 页面切换过渡增强

---

### 4.3 性能考虑

#### CSS 动画性能

| 技术 | 性能影响 | 说明 |
|------|---------|------|
| `transform` | ✅ GPU 加速 | 无重排重绘，推荐使用 |
| `opacity` | ✅ GPU 加速 | 无重排重绘，推荐使用 |
| `max-height` | ⚠️ 重排 | 仅用于引用展开（一次性），影响可控 |
| JavaScript scroll 监听 | ⚠️ 主线程 | 使用 `requestAnimationFrame` 节流 |

#### 优化措施

1. **消息进入动画：**
   - 使用 CSS `@keyframes`，无 JS 计算
   - 仅对新消息应用动画类
   - 500ms 后自动移除动画类（避免重复触发）

2. **滚动监听：**
   - 使用 `passive: true` 监听器（不阻塞滚动）
   - 避免在 scroll 回调中读取布局属性
   - 已有 `requestAnimationFrame` 节流

3. **骨架屏：**
   - 纯 CSS shimmer 动画，无 JS
   - 使用 `background-size` 而非额外 DOM 元素

#### 性能基准

- 动画帧率：保持 60fps
- 首屏加载时间：无明显增加（骨架屏反而减少感知时间）
- 内存占用：增加 < 1MB（骨架屏组件）

---

### 4.4 无障碍考虑

#### 1. 动画偏好

**所有动画组件必须尊重 `prefers-reduced-motion`：**

```css
@media (prefers-reduced-motion: reduce) {
  /* 禁用所有非必要动画 */
  .message-enter,
  .scroll-to-bottom,
  .citations-content,
  .skeleton-cell {
    animation: none !important;
    transition: opacity 100ms !important; /* 仅保留最简过渡 */
  }
}
```

#### 2. 键盘导航

- 滚动到底部按钮：`tabindex="0"`，支持 Enter/Space 触发
- 引用详情：保持 `<details>` 原生键盘支持
- 页面切换：焦点管理（Vue Router 自动处理）

#### 3. 屏幕阅读器

- 滚动到底部按钮：`aria-label="滚动到最新消息"`
- 骨架屏：`aria-hidden="true"`（纯装饰性）
- 消息进入：无额外 ARIA（不影响内容可读性）

---

### 4.5 浏览器兼容性

| 特性 | Chrome | Firefox | Safari | Edge |
|------|--------|---------|--------|------|
| CSS Transform/Opacity | ✅ 全版本 | ✅ 全版本 | ✅ 全版本 | ✅ 全版本 |
| `scroll-behavior: smooth` | ✅ 61+ | ✅ 36+ | ✅ 15.4+ | ✅ 79+ |
| 自定义滚动条样式 | ✅ 全版本 | ✅ 64+ | ⚠️ 有限支持 | ✅ 全版本 |
| `backdrop-filter` | ✅ 76+ | ✅ 103+ | ✅ 9+ | ✅ 79+ |
| `:focus-visible` | ✅ 86+ | ✅ 85+ | ✅ 15.4+ | ✅ 86+ |

**降级策略：**
- Safari 滚动条：保持默认样式，不影响功能
- 旧版浏览器：动画降级为淡入淡出（无滑动）

---

## 5. 测试计划

### 5.1 功能测试

| 测试场景 | 预期结果 |
|---------|---------|
| 发送消息 | 用户消息和 AI 回复平滑滑入 |
| 流式输出 | AI 消息整体滑入后，内容逐步显示 |
| 用户向上滚动 | 新消息到达时不自动滚动，显示按钮 |
| 点击"回到底部" | 平滑滚动到最新消息，按钮消失 |
| 刷新知识库 | 显示骨架屏，数据到达后平滑过渡 |
| 点击引用详情 | 内容平滑展开，再次点击平滑收起 |
| 页面切换 | 聊天↔知识库有方向感的滑动过渡 |
| 系统偏好：减少动画 | 所有动画禁用，功能正常 |

### 5.2 性能测试

| 测试项 | 预期结果 |
|---------|---------|
| 连续发送 10 条消息 | 无卡顿，帧率 ≥ 55fps |
| 长对话历史（100+ 条） | 滚动流畅，内存无泄漏 |
| 快速切换页面 10 次 | 无明显延迟，动画正常 |
| 移动端（3G 网络） | 骨架屏正常显示，首屏时间 < 3s |

### 5.3 无障碍测试

| 测试项 | 预期结果 |
|---------|---------|
| VoiceOver/NVDA 测试 | 所有交互可读，无冗余信息 |
| 仅键盘导航 | 所有功能可达，焦点顺序合理 |
| 高对比度模式 | 滚动条、按钮可见性良好 |

---

## 6. 风险与缓解

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|---------|
| 滚动监听性能问题 | 低 | 中 | 使用 passive 监听器 + RAF 节流 |
| 动画导致视觉疲劳 | 中 | 低 | 提供系统级禁用选项 |
| Safari 滚动条样式不一致 | 高 | 低 | 优雅降级，不影响功能 |
| 骨架屏与真实内容高度不一致 | 低 | 低 | 使用弹性高度设计 |
| 页面切换动画打断用户操作 | 低 | 中 | 动画时长控制在 250ms 内 |

---

## 7. 未来扩展

### 7.1 潜在优化项

- **消息气泡微动画：** 鼠标悬停时轻微上浮阴影
- **打字机效果：** AI 回复逐字显示（可选功能）
- **发送按钮动画：** 点击时涟漪扩散效果
- **历史消息懒加载：** 向上滚动时加载更早消息

### 7.2 性能监控

- 使用 Lighthouse 持续监控性能评分
- 添加 Performance Observer 监控长任务
- 用户反馈收集（动画流畅度）

---

## 8. 附录

### 8.1 参考资料

- [Vue 3 Transition 文档](https://vuejs.org/guide/built-ins/transition.html)
- [CSS Animation Performance](https://web.dev/animations-guide/)
- [MDN: prefers-reduced-motion](https://developer.mozilla.org/en-US/docs/Web/CSS/@media/prefers-reduced-motion)
- [Element Plus 动画指南](https://element-plus.org/en-US/guide/animation.html)

### 8.2 相关文档

- 项目 README：`../../README.md`
- 设计系统：`design-system/MASTER.md`

---

**文档维护：** 请在实施过程中更新本文档的状态和风险项。