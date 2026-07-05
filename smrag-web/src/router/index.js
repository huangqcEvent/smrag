import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/chat'
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/Chat.vue'),
    meta: { title: '智能问答' }
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('@/views/Knowledge.vue'),
    meta: { title: '知识库管理' }
  },
  {
    path: '/product',
    name: 'Product',
    component: () => import('@/views/Product.vue'),
    meta: { title: '产品管理' }
  },
  {
    path: '/manager',
    name: 'Manager',
    component: () => import('@/views/Manager.vue'),
    meta: { title: '管理人管理' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title || '私募AI专家'
  next()
})

export default router