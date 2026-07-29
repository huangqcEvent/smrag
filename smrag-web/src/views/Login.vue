<template>
  <div
    ref="loginPage"
    class="login-page"
    @pointerenter="handlePointerEnter"
    @pointermove="handlePointerMove"
    @pointerleave="handlePointerLeave"
  >
    <LoginScene
      :lens-active="lensActive && !lensBlocked"
      :username="form.username"
      :password="form.password"
      :show-password="showPassword"
      :loading="loading"
      :active-field="activeField"
      @update:username="form.username = $event"
      @update:password="form.password = $event"
      @toggle-password="showPassword = !showPassword"
      @focus-field="activeField = $event"
      @lens-blocked="lensBlocked = $event"
      @submit="handleLogin"
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
  createPointerLensController,
  isMousePointer,
  toLocalPointerPosition
} from '@/utils/pointerLens'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const loginPage = ref(null)
const lensActive = ref(false)
const lensBlocked = ref(false)
const loading = ref(false)
const showPassword = ref(false)
const activeField = ref('')

const form = reactive({
  username: '',
  password: ''
})

let lensController = null
let lensMediaQueries = []

const setLensPosition = (x, y) => {
  const page = loginPage.value
  if (!page) return
  const position = toLocalPointerPosition(
    { clientX: x, clientY: y },
    page.getBoundingClientRect()
  )
  page.style.setProperty('--lens-x', `${position.x}px`)
  page.style.setProperty('--lens-y', `${position.y}px`)
}

const handlePointerEnter = event => {
  if (!lensController || !isMousePointer(event)) return
  setLensPosition(event.clientX, event.clientY)
  lensController.enter(event)
}

const handlePointerMove = event => {
  if (!isMousePointer(event)) return
  lensController?.move(event)
}

const handlePointerLeave = event => {
  if (!isMousePointer(event)) return
  lensBlocked.value = false
  lensController?.leave()
}

const disablePointerLens = () => {
  lensController?.destroy()
  lensController = null
  lensActive.value = false
  lensBlocked.value = false
}

const updateLensAvailability = () => {
  const [hover, finePointer] = lensMediaQueries
  const enabled = hover && canUsePointerLens({
    canHover: hover.matches,
    hasFinePointer: finePointer.matches
  })

  if (!enabled) {
    disablePointerLens()
    return
  }
  if (lensController) return
  lensController = createPointerLensController({
    onPosition: setLensPosition,
    onActive(active) {
      lensActive.value = active
    }
  })
}

onMounted(() => {
  lensMediaQueries = [
    window.matchMedia('(any-hover: hover)'),
    window.matchMedia('(any-pointer: fine)')
  ]
  lensMediaQueries.forEach(query => {
    query.addEventListener('change', updateLensAvailability)
  })
  updateLensAvailability()
})

onBeforeUnmount(() => {
  lensMediaQueries.forEach(query => {
    query.removeEventListener('change', updateLensAvailability)
  })
  disablePointerLens()
})

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
  min-height: max(100vh, 44rem);
  min-height: max(100dvh, 44rem);
  overflow: hidden;
  background: oklch(0.992 0.002 165);
}

@media (max-width: 900px) {
  .login-page {
    min-height: 52rem;
  }
}
</style>
