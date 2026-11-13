import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import Login from './pages/Login.vue'
import Home from './pages/Home.vue'
import Users from './pages/Users.vue'
import Departments from './pages/Departments.vue'
import Roles from './pages/Roles.vue'
import Resources from './pages/Resources.vue'
import AuditLogs from './pages/AuditLogs.vue'
import { session, refreshSession, notify, canRoute } from './api.js'
import './style.css'
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: Home, meta: { title: '工作总览' } },
    { path: '/login', component: Login },
    { path: '/users', component: Users, meta: { title: '人员管理', capability: '/users' } },
    { path: '/departments', component: Departments, meta: { title: '部门管理', capability: '/departments' } },
    { path: '/roles', component: Roles, meta: { title: '角色与权限', capability: '/roles', global: true } },
    { path: '/resources', component: Resources, meta: { title: '资源与API', capability: '/systems', global: true } },
    { path: '/logs', component: AuditLogs, meta: { title: '鉴权日志', capability: '/authorization-logs', global: true } },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})
/** 每次工作区导航读取最新能力，直达URL仍须通过服务端能力及范围。 */
router.beforeEach(async function(to) {
  if (to.path === '/login') return true
  if (!session.token) return '/login'
  try {
    await refreshSession()
    if (to.meta.capability && !canRoute('GET', to.meta.capability, to.meta.global)) {
      notify('当前账号没有该页面的访问权限。')
      return '/'
    }
  } catch (error) { notify(error.message); return session.me ? false : '/login' }
  return true
})
createApp(App).use(router).mount('#app')
