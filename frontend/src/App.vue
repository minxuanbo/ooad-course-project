<script setup>
import { onMounted, onUnmounted, watch, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { session, api, setToken, refreshSession, notify } from './api.js'
const route = useRoute(), router = useRouter()
const revision = ref(0)
const nav = [['portal','我的应用'],['dashboard','工作台'],['users','用户与授权'],['roles','角色与权限'],['apps','业务系统'],['logs','鉴权日志']]
let timer, syncing = false
async function logout() {
  try { await api('/auth/logout', 'POST'); setToken(''); await router.replace('/login') }
  catch (error) { notify(error.message) }
}
async function sync() {
  if (!session.token || route.path === '/login' || syncing) return
  syncing = true
  try {
    const previous = session.me?.canManage
    const me = await refreshSession()
    if (previous && !me.canManage) {
      revision.value++
      notify('后台管理权限已回收，已返回我的应用。')
      await router.replace('/portal')
    }
  } catch (error) { notify(error.message) }
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
      <nav><template v-for="item in nav" :key="item[0]"><RouterLink v-if="item[0] === 'portal' || session.me.canManage" :to="'/' + item[0]" active-class="active"><span>◇</span>{{ item[1] }}</RouterLink></template></nav>
      <div class="sidebar-bottom"><span class="dot"></span> 统一权限服务<small>Spring Boot + Vue · RBAC0</small></div>
    </aside>
    <div class="workspace">
      <header class="topbar"><span>市政集团 <i>/</i> 权限管理平台</span><div class="profile"><span class="avatar">{{ session.me.user.name?.[0] }}</span>{{ session.me.user.name }}<button class="text-button" @click="logout">退出</button></div></header>
      <section class="content">
        <div class="page-heading"><div><div class="eyebrow">ACCESS CONSOLE</div><h1>{{ route.meta.title }}</h1><p class="muted">按角色授予权限，每次请求由后端重新校验。</p></div><button class="secondary" @click="revision++">↻ 刷新数据</button></div>
        <RouterView :key="route.path + revision" />
        <footer class="content-footer">市政公司统一权限管理系统<span>权限按角色合并 · 未授权访问默认拒绝</span></footer>
      </section>
    </div>
  </div>
  <div id="notice" :class="{ visible: session.notice }" role="status" aria-live="polite">{{ session.notice }}</div>
</template>
