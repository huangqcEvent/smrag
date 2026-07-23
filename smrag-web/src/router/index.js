import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/',
    redirect: '/chat'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: {
      title: '登录',
      public: true
    }
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/Chat.vue'),
    meta: {
      title: '智能问答',
      transition: 'page-slide',
      requiresAuth: true
    }
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('@/views/Knowledge.vue'),
    meta: {
      title: '知识库管理',
      transition: 'page-slide-reverse',
      requiresAuth: true
    }
  },
  {
    path: '/users',
    name: 'Users',
    component: () => import('@/views/Users.vue'),
    meta: {
      title: '用户管理',
      transition: 'page-slide',
      requiresAuth: true,
      roles: ['ADMIN']
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
  // 设置页面标题
  document.title = `${to.meta.title || '基金智库'} | 基金智库`

  // 公开页面直接放行
  if (to.meta.public) {
    return next()
  }

  // 需要认证的页面
  const authStore = useAuthStore()

  // 检查是否已登录
  if (!authStore.isLoggedIn) {
    return next({
      path: '/login',
      query: { redirect: to.fullPath }
    })
  }

  // 如果还没有用户信息，尝试获取
  if (!authStore.user) {
    try {
      await authStore.fetchCurrentUser()
    } catch (error) {
      console.error('Failed to fetch user info:', error)
    }
  }

  // 检查角色权限
  if (to.meta.roles && to.meta.roles.length > 0) {
    if (!to.meta.roles.includes(authStore.userRole)) {
      // 没有权限，跳转到首页
      return next('/chat')
    }
  }

  next()
})

export default router
