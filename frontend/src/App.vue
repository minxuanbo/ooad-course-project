<script setup>
import { useRoute, useRouter } from 'vue-router'
import { session, api, setToken, notify, can } from './api.js'
const route = useRoute(), router = useRouter()
const nav = [['logs', '鉴权日志', 'rbac.logs.read']]
async function logout() {
  try { await api('/auth/logout', 'POST'); setToken(''); await router.replace('/login') }
  catch (error) { notify(error.message); setToken(''); await router.replace('/login') }
}
</script>
<template>
  <RouterView v-if="route.path === '/login'" />
  <div v-else-if="session.me" class="shell">
    <aside class="sidebar">
      <div class="brand"><span class="mark">市</span><div>统一权限管理<small>市政集团</small></div></div>
      <div class="nav-label">管理空间</div>
      <nav><template v-for="item in nav" :key="item[0]"><RouterLink v-if="can(item[2])" :to="'/' + item[0]" active-class="active"><span>◇</span>{{ item[1] }}</RouterLink></template></nav>
      <div class="sidebar-bottom"><span class="dot"></span> 统一权限服务<small>Spring Boot + Vue · RBAC0</small></div>
    </aside>
    <div class="workspace">
      <header class="topbar"><span>市政集团 <i>/</i> 权限管理平台</span><div class="profile"><span class="avatar">{{ session.me.user.name?.[0] }}</span>{{ session.me.user.name }}<button class="text-button" @click="logout">退出</button></div></header>
      <section class="content">
        <div class="page-heading"><div><div class="eyebrow">ACCESS CONSOLE</div><h1>{{ route.meta.title }}</h1><p class="muted">授权按角色授予；实际有效权限决定可执行的操作与可见入口。</p></div></div>
        <RouterView :key="route.path" />
        <footer class="content-footer">市政公司统一权限管理系统<span>权限按角色合并 · 未授权访问默认拒绝</span></footer>
      </section>
    </div>
  </div>
  <div id="notice" :class="{ visible: session.notice }" role="status" aria-live="polite">{{ session.notice }}</div>
</template>
