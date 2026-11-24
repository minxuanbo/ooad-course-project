<script setup>
import { watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { session, api, setToken, notify, canRoute } from './api.js'
import AppIcon from './components/AppIcon.vue'
import { resourcePageAvailable } from './lib/presentation.js'
const route = useRoute(),
  router = useRouter()
const navigation = [
  { path: '/', label: '总览', group: '工作台', icon: 'overview', capability: '/overview' },
  { path: '/applications', label: '应用管理', group: '权限管理', icon: 'resource', capability: '/systems/page', global: true },
  {
    path: '/departments',
    label: '部门管理',
    group: '组织管理',
    icon: 'building',
    capability: '/departments',
  },
  { path: '/users', label: '人员管理', group: '组织管理', icon: 'users', capability: '/users' },
  {
    path: '/roles',
    label: '角色与权限',
    group: '权限管理',
    icon: 'shield',
    capability: '/roles',
  },
  {
    path: '/resources',
    label: '资源与API',
    group: '权限管理',
    icon: 'resource',
    resource: true,
    global: true,
  },
  {
    path: '/logs',
    label: '鉴权日志',
    group: '安全审计',
    icon: 'history',
    capability: '/authorization-logs',
    global: true,
  },
]
/** 分组只包含最新服务端能力允许的入口，撤权后不留下空分组。 */
const navGroups = computed(() => {
  const visible = navigation.filter((item) =>
    item.resource
      ? resourcePageAvailable(canRoute)
      : !item.capability || canRoute('GET', item.capability, item.global),
  )
  if (!visible.some((item) => item.path === '/'))
    visible.unshift({ path: '/', label: '工作台', group: '工作台', icon: 'overview' })
  return ['工作台', '组织管理', '权限管理', '安全审计']
    .map((label) => ({ label, items: visible.filter((item) => item.group === label) }))
    .filter((group) => group.items.length)
})
const currentNavigation = computed(() => route.path === '/' && !canRoute('GET', '/overview')
  ? { label: '工作台', group: '工作台' }
  : navigation.find((item) => item.path === route.path))
const descriptions = {
  '/': '查看组织概况，从当前管理范围开始。',
  '/departments': '新增下级、修改名称、删除空部门，按组织范围维护。',
  '/users': '查看人员职位与已授角色，按组织范围调整授权。',
  '/applications': '登记和维护受管应用，明确接入状态与访问边界。',
  '/roles': '维护共享角色，明确每一项可执行能力。',
  '/resources': '连接菜单、按钮与实际受控接口。',
  '/logs': '检索登录与鉴权事件，追踪每一次访问判断。',
}
watch(
  () => session.token,
  (token) => {
    if (!token) router.replace('/login')
  },
)
/** 退出无论网络结果均清除本机令牌，服务端令牌另受有效期限制。 */
async function logout() {
  try {
    await api('/auth/logout', 'POST')
  } catch (error) {
    notify(error.message)
  } finally {
    setToken('')
    await router.replace('/login')
  }
}
</script>
<template>
  <RouterView v-if="route.path === '/login'" />
  <div v-else-if="session.me" class="shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="mark app-logo" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
        <div>
          市政权限
          <small>MUNICIPAL ACCESS</small>
        </div>
      </div>
      <nav class="sidebar-nav" aria-label="管理工作区">
        <div v-for="group in navGroups" :key="group.label" class="nav-group">
          <div class="nav-label">{{ group.label }}</div>
          <RouterLink
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            active-class=""
            exact-active-class="active"
          >
            <AppIcon :name="item.icon" />
            <span>{{ item.label }}</span>
            <span class="nav-active-dot" aria-hidden="true"></span>
          </RouterLink>
        </div>
      </nav>
      <div class="sidebar-bottom">
        <span class="dot"></span>
        统一权限服务
        <small>按最新授权判定访问</small>
      </div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <span> {{ currentNavigation?.group }}<i>›</i>{{ currentNavigation?.label }} </span>
        <div class="profile">
          <span class="avatar">{{ session.me.user.name?.[0] }}</span>
          <div>
            {{ session.me.user.name }}
            <small>{{ session.me.user.username }}</small>
          </div>
          <button class="text-button" @click="logout">退出</button>
        </div>
      </header>
      <section class="content">
        <div class="page-heading">
          <div>
            <h1>{{ currentNavigation?.label || route.meta.title }}</h1>
            <p class="muted">{{ descriptions[route.path] }}</p>
          </div>
          <div id="page-actions" class="page-actions"></div>
        </div>
        <RouterView :key="route.path" />
        <footer class="content-footer">
          市政公司统一权限管理系统
          <span>撤权即时生效 · 未授权访问默认拒绝</span>
        </footer>
      </section>
    </div>
  </div>
  <div id="notice" :class="{ visible: session.notice }" role="status" aria-live="polite">
    {{ session.notice }}
  </div>
</template>
<style scoped>
.page-heading h1 {
  font-size: 26px;
  letter-spacing: -0.5px;
  color: #182944;
}
.page-heading p {
  font-size: 12px;
  color: #8b9bb3;
  margin-bottom: 0;
}
.topbar {
  height: 70px;
}
.topbar > span {
  color: #8294af;
}
.content {
  padding-top: 32px;
}
.page-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 14px;
}
.page-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 4px;
}
.app-logo {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  grid-template-rows: repeat(2, 1fr);
  place-items: stretch;
  gap: 3px;
  width: 34px;
  height: 34px;
  padding: 8px;
  border-radius: 9px;
  box-shadow: 0 3px 10px #2563eb20;
}
.app-logo i {
  display: block;
  background: white;
  border-radius: 1px;
}
.app-logo i:last-child {
  opacity: 0.6;
}
.sidebar {
  padding: 24px 12px;
}
.sidebar .brand {
  gap: 11px;
  padding: 0 6px;
  color: #182944;
}
.sidebar .brand small {
  letter-spacing: 1.1px;
  font-size: 10px;
}
.sidebar-nav {
  margin-top: 32px;
  overflow-y: auto;
  padding-bottom: 22px;
}
.nav-group + .nav-group {
  margin-top: 25px;
}
.nav-label {
  margin: 0 14px 8px;
  color: #9ca9bc;
  font-size: 10px;
  letter-spacing: 0.5px;
}
.sidebar-nav a {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 42px;
  margin: 3px 0;
  padding: 11px 14px;
  border-radius: 8px;
  color: #2e4261;
  font-size: 13px;
  font-weight: 550;
}
.sidebar-nav a:hover {
  background: #f5f8fd;
}
.sidebar-nav a.active {
  background: #edf3ff;
  color: #3865ec;
}
.sidebar-nav a .ui-icon {
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
}
.nav-active-dot {
  display: none;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  margin-left: auto;
  flex: 0 0 auto;
}
.active .nav-active-dot {
  display: block;
}
@media (max-width: 720px) {
  .sidebar {
    padding: 14px 16px;
  }
  .sidebar-nav {
    display: flex;
    gap: 5px;
    overflow-x: auto;
    margin-top: 14px;
    padding: 0 0 3px;
  }
  .nav-group {
    display: contents;
  }
  .nav-label {
    display: none;
  }
  .sidebar-nav a {
    margin: 0;
    min-height: 38px;
    padding: 8px 11px;
    gap: 7px;
    font-size: 12px;
  }
  .sidebar-nav a .ui-icon {
    width: 16px;
    height: 16px;
  }
  .nav-active-dot,
  .active .nav-active-dot {
    display: none;
  }
}
</style>
