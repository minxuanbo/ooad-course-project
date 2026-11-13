<script setup>
import { watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { session, api, setToken, notify, canRoute } from './api.js'
const route = useRoute(), router = useRouter()
const navigation = [
  ['/', '工作总览', null, false], ['/departments', '部门管理', '/departments', false],
  ['/users', '人员管理', '/users', false], ['/roles', '角色与权限', '/roles', true],
  ['/resources', '资源与API', '/systems', true], ['/logs', '鉴权日志', '/authorization-logs', true]
]
const nav = computed(() => navigation.filter(item => !item[2] || canRoute('GET', item[2], item[3])))
watch(() => session.token, token => { if (!token) router.replace('/login') })
/** 退出无论网络结果均清除本机令牌，服务端令牌另受有效期限制。 */
async function logout() {
  try { await api('/auth/logout', 'POST') }
  catch (error) { notify(error.message) }
  finally { setToken(''); await router.replace('/login') }
}
</script>
<template>
  <RouterView v-if="route.path === '/login'" />
  <div v-else-if="session.me" class="shell">
    <aside class="sidebar">
      <div class="brand"><span class="mark">市</span><div>统一权限管理<small>市政集团</small></div></div>
      <div class="nav-label">管理空间</div>
      <nav aria-label="管理工作区"><RouterLink v-for="item in nav" :key="item[0]" :to="item[0]" active-class="active" :exact-active-class="item[0] === '/' ? 'active' : ''"><span aria-hidden="true">◇</span>{{ item[1] }}</RouterLink></nav>
      <div class="sidebar-bottom"><span class="dot"></span> 统一权限服务<small>按最新授权判定访问</small></div>
    </aside>
    <div class="workspace">
      <header class="topbar"><span>市政集团 <i>/</i> 权限管理平台</span><div class="profile"><span class="avatar">{{ session.me.user.name?.[0] }}</span><div>{{ session.me.user.name }}<small>{{ session.me.user.departmentName }}</small></div><button class="text-button" @click="logout">退出</button></div></header>
      <section class="content">
        <div class="page-heading"><div><div class="eyebrow">ACCESS CONSOLE</div><h1>{{ route.meta.title }}</h1><p class="muted">授权按角色授予，人员操作受部门范围和管理级别共同约束。</p></div></div>
        <RouterView :key="route.path" />
        <footer class="content-footer">市政公司统一权限管理系统<span>撤权即时生效 · 未授权访问默认拒绝</span></footer>
      </section>
    </div>
  </div>
  <div id="notice" :class="{ visible: session.notice }" role="status" aria-live="polite">{{ session.notice }}</div>
</template>
