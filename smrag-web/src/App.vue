<template>
  <div class="app-shell">
    <header class="app-header">
      <button class="brand" type="button" aria-label="SMRAG 智能知识助手" @click="goHome">
        <span class="brand-mark">S</span>
        <span>SMRAG</span>
      </button>

      <nav aria-label="主导航">
        <router-link to="/chat" :class="{ active: route.path === '/chat' }">
          <el-icon><ChatDotRound /></el-icon>
          <span>智能问答</span>
        </router-link>
        <router-link to="/knowledge" :class="{ active: route.path === '/knowledge' }">
          <el-icon><Collection /></el-icon>
          <span>知识库</span>
        </router-link>
      </nav>
    </header>

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

<script setup>
import { ChatDotRound, Collection } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const goHome = () => router.push('/chat')
</script>

<style scoped>
.app-shell {
  height: 100vh;
  display: grid;
  grid-template-rows: 64px minmax(0, 1fr);
  background: var(--bg-primary);
}

.app-header {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 0 clamp(16px, 4vw, 40px);
  border-bottom: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 86%, transparent);
  backdrop-filter: blur(20px) saturate(155%);
  z-index: 20;
}

.brand {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  font-size: 0.88rem;
  font-weight: 700;
  letter-spacing: 0;
  cursor: pointer;
}

.brand-mark {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 7px;
  background: var(--accent);
  color: var(--accent-contrast);
  font-size: 0.78rem;
}

nav {
  display: flex;
  align-items: center;
  gap: 4px;
}

nav a {
  min-height: 36px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 11px;
  border-radius: 7px;
  color: var(--text-secondary);
  font-size: 0.8rem;
  font-weight: 550;
  text-decoration: none;
  transition: background-color 160ms ease-out, color 160ms ease-out;
}

nav a:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}

nav a.active {
  background: var(--accent-soft);
  color: var(--accent);
}

nav a:active,
.brand:active {
  transform: scale(0.97);
}

.app-main {
  min-height: 0;
  overflow: hidden;
}

.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 150ms ease-out;
}

.page-fade-enter-from,
.page-fade-leave-to {
  opacity: 0;
}

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

@media (max-width: 520px) {
  .app-header {
    gap: 8px;
  }

  nav a {
    padding: 0 9px;
  }

  nav a span {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .page-fade-enter-active,
  .page-fade-leave-active,
  .page-slide-enter-active,
  .page-slide-leave-active {
    transition: opacity 120ms;
  }

  .page-fade-enter-from,
  .page-fade-leave-to,
  .page-slide-enter-from,
  .page-slide-leave-to,
  .page-slide-reverse-enter-from,
  .page-slide-reverse-leave-to {
    transform: none;
  }
}

@media (prefers-reduced-transparency: reduce) {
  .app-header {
    background: var(--surface);
    backdrop-filter: none;
  }
}
</style>
