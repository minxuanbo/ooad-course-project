import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import Login from './pages/Login.vue'
import AuditLogs from './pages/AuditLogs.vue'
import { session, refreshSession, notify, can } from './api.js'
import './style.css'
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: Login },
    { path: '/logs', component: AuditLogs, meta: { admin: true, title: '鉴权日志' } },
    { path: '/:pathMatch(.*)*', redirect: '/login' }
  ]
})
router.beforeEach(async function(to) {
  if (to.path === '/login') return true
  if (!session.token) return '/login'
  try {
    await refreshSession()
    if (to.meta.admin && !can('rbac.logs.read')) {
      notify('当前账号没有该页面的访问权限。')
      return '/login'
    }
  } catch (error) { notify(error.message); return '/login' }
  return true
})
createApp(App).use(router).mount('#app')
