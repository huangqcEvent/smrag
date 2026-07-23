<template>
  <div
    ref="loginPage"
    class="login-page"
    @pointerenter="handlePointerEnter"
    @pointermove="handlePointerMove"
    @pointerleave="handlePointerLeave"
  >
    <LoginScene
      language="zh"
      interactive
      :username="form.username"
      :password="form.password"
      :show-password="showPassword"
      :loading="loading"
      :active-field="activeField"
      @update:username="form.username = $event"
      @update:password="form.password = $event"
      @toggle-password="showPassword = !showPassword"
      @focus-field="activeField = $event"
      @submit="handleLogin"
    />

    <LoginScene
      class="login-english-layer"
      language="en"
      :interactive="false"
      :username="form.username"
      :password="form.password"
      :show-password="showPassword"
      :loading="loading"
      :active-field="activeField"
    />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import LoginScene from '@/components/LoginScene.vue'
import { useAuthStore } from '@/stores/auth'
import { notifyError } from '@/utils/errorFeedback'
import {
  canUsePointerLens,
  createPointerLensController
} from '@/utils/pointerLens'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const loginPage = ref(null)
const loading = ref(false)
const showPassword = ref(false)
const activeField = ref('')

const form = reactive({
  username: '',
  password: ''
})

let lensController = null

const setLensPosition = (x, y) => {
  loginPage.value?.style.setProperty('--lens-x', `${x}px`)
  loginPage.value?.style.setProperty('--lens-y', `${y}px`)
}

const handlePointerEnter = event => {
  if (!lensController) return
  setLensPosition(event.clientX, event.clientY)
  lensController.enter(event)
}

const handlePointerMove = event => {
  lensController?.move(event)
}

const handlePointerLeave = () => {
  lensController?.leave()
}

onMounted(() => {
  const hover = window.matchMedia('(hover: hover)')
  const finePointer = window.matchMedia('(pointer: fine)')
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')

  if (!canUsePointerLens({
    canHover: hover.matches,
    hasFinePointer: finePointer.matches,
    reducedMotion: reducedMotion.matches
  })) return

  lensController = createPointerLensController({
    onPosition: setLensPosition,
    onActive(active) {
      loginPage.value?.classList.toggle('is-lens-active', active)
    }
  })
  loginPage.value?.classList.add('has-pointer-lens')
})

onBeforeUnmount(() => lensController?.destroy())

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.error('请输入用户名和密码')
    return
  }

  loading.value = true
  try {
    await authStore.login(form.username, form.password)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || '/chat'
    router.push(redirect)
  } catch (error) {
    notifyError(error, '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  --lens-x: 50vw;
  --lens-y: 50vh;
  --lens-radius: clamp(10.625rem, 16vw, 14.375rem);
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background: oklch(0.975 0.006 165);
  isolation: isolate;
}

.login-english-layer {
  z-index: 4;
  pointer-events: none;
  clip-path: circle(0 at var(--lens-x) var(--lens-y));
  transition: clip-path 180ms cubic-bezier(0.22, 1, 0.36, 1);
  will-change: clip-path;
}

.has-pointer-lens.is-lens-active .login-english-layer {
  clip-path: circle(var(--lens-radius) at var(--lens-x) var(--lens-y));
}

@media (hover: none), (pointer: coarse), (max-width: 720px) {
  .login-english-layer {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-english-layer {
    display: none;
  }
}
</style>
