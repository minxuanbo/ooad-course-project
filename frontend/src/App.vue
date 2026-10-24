<script setup>
import { onMounted, onUnmounted, watch, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { session, api, setToken, refreshSession, notify, can, routePermissions, scopeSummary } from './api.js'
const route = useRoute(), router = useRouter()
const revision = ref(0)
const nav = [['portal','我的应用'],['dashboard','工作台'],['organizations','组织管理'],['users','用户与授权'],['roles','角色与权限'],['apps','业务系统'],['logs','鉴权日志']]
let timer, syncing = false
async function logout() {
  try { await api('/auth/logout', 'POST'); setToken(''); await router.replace('/login') }
  catch (error) { notify(error.message); setToken(''); await router.replace('/login') }
}
async function sync() {
  if (!session.token || route.path === '/login' || syncing) return
  syncing = true
  try {
    const previous = JSON.stringify(session.me?.managementGrants)
    const me = await refreshSession()
    if (previous !== JSON.stringify(me.managementGrants)) {
      revision.value++
      notify('管理权限或范围已变更，已刷新当前数据。')
      if (route.meta.admin && !can(routePermissions[route.path.slice(1)])) await router.replace('/portal')
    }
  } catch (error) { session.me = null; notify(error.message); await router.replace('/login') }
  finally { syncing = false }
}
watch(function() { return session.token }, function(token) { if (!token) router.replace('/login') })
onMounted(function() { timer = setInterval(sync, 3000); window.addEventListener('focus', sync) })
onUnmounted(function() { clearInterval(timer); window.removeEventListener('focus', sync) })
</script>
<template>
  <RouterView v-if="route.path === '/login'" />
  <div v-else-if="session.me" class="shell">
    <aside class="sidebar">
      <div class="brand"><span class="mark">市</span><div>统一权限管理<small>市政集团</small></div></div>
      <div class="nav-label">{{ session.me.canManage ? '管理空间' : '应用空间' }}</div>
      <nav><template v-for="item in nav" :key="item[0]"><RouterLink v-if="item[0] === 'portal' || can(routePermissions[item[0]])" :to="'/' + item[0]" active-class="active"><span>◇</span>{{ item[1] }}</RouterLink></template></nav>
      <div class="sidebar-bottom"><span class="dot"></span> 统一权限服务<small>Spring Boot + Vue · RBAC0</small></div>
    </aside>
    <div class="workspace">
      <header class="topbar"><span>市政集团 <i>/</i> 权限管理平台</span><div class="profile"><span class="avatar">{{ session.me.user.name?.[0] }}</span>{{ session.me.user.name }}<button class="text-button" @click="logout">退出</button></div></header>
      <section class="content">
        <div class="page-heading"><div><div class="eyebrow">ACCESS CONSOLE</div><h1>{{ route.meta.title }}</h1><p class="muted">业务权限按角色授予；管理范围：{{ scopeSummary() }}。</p></div><button class="secondary" @click="revision++">↻ 刷新数据</button></div>
        <RouterView :key="route.path + revision" />
        <footer class="content-footer">市政公司统一权限管理系统<span>权限按角色合并 · 未授权访问默认拒绝</span></footer>
      </section>
    </div>
  </div>
  <div id="notice" :class="{ visible: session.notice }" role="status" aria-live="polite">{{ session.notice }}</div>
</template>
