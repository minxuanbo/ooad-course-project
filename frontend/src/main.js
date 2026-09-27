import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import Login from './pages/Login.vue'
import Portal from './pages/Portal.vue'
import Users from './pages/Users.vue'
import Management from './pages/Management.vue'
import { session, refreshSession, notify } from './api.js'
import './style.css'
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/portal' },
    { path: '/login', component: Login },
    { path: '/portal', component: Portal, meta: { title: '我的应用' } },
    { path: '/users', component: Users, meta: { admin: true, title: '用户与授权' } },
    { path: '/dashboard', component: Management, meta: { admin: true, title: '工作台' } },
    { path: '/roles', component: Management, meta: { admin: true, title: '角色与权限' } },
    { path: '/apps', component: Management, meta: { admin: true, title: '业务系统' } },
    { path: '/logs', component: Management, meta: { admin: true, title: '鉴权日志' } },
    { path: '/:pathMatch(.*)*', redirect: '/portal' }
  ]
})
router.beforeEach(async function(to) {
  if (to.path === '/login') return true
  if (!session.token) return '/login'
  try {
    const me = await refreshSession()
    if (to.meta.admin && !me.canManage) {
      notify('您没有权限管理后台的访问权限。')
      return '/portal'
    }
  } catch (error) { notify(error.message); return '/login' }
  return true
})
createApp(App).use(router).mount('#app')
