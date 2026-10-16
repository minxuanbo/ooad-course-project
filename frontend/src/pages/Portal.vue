<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, notify } from '../api.js'
const route = useRoute(), router = useRouter()
const apps = ref([]), status = ref('正在同步权限…'), loading = ref(true), busy = ref(false)
let timer, syncing = false, alive = true
async function sync() {
  if (syncing) return false
  syncing = true
  try {
    const data = await api('/me/applications')
    if (!alive) return false
    apps.value = data
    status.value = '权限已同步 · ' + new Date().toLocaleTimeString('zh-CN')
    return true
  } catch (error) {
    if (alive) { apps.value = []; status.value = error.message + ' 未使用旧权限。' }
    return false
  } finally { syncing = false; if (alive) loading.value = false }
}
async function enter(id) {
  if (busy.value) return
  busy.value = true
  try {
    const result = await api('/me/applications/' + id + '/enter', 'POST')
    if (alive && result.allowed) await router.push('/applications/' + id)
  } catch (error) { if (alive) { notify(error.message); await sync() } }
  finally { busy.value = false }
}
onMounted(async function() {
  const ok = await sync()
  if (!alive) return
  if (route.query.initial) {
    await router.replace('/portal')
    if (ok && apps.value.length === 1) await enter(apps.value[0].id)
  }
  if (!alive) return
  timer = setInterval(sync, 3000); window.addEventListener('focus', sync)
})
onUnmounted(function() { alive = false; clearInterval(timer); window.removeEventListener('focus', sync) })
</script>
<template>
  <div class="welcome"><div><span class="pill">我的应用</span><h2>{{ apps.length }} 个系统有权限访问</h2><p>仅显示已获授权的系统。权限每 3 秒同步，进入时再次校验。</p></div></div>
  <p class="muted" role="status">{{ status }}</p>
  <div v-if="loading" class="empty">正在加载…</div>
  <section v-else-if="!apps.length" class="card empty">暂无可用应用，请确认连接正常或联系管理员分配权限。</section>
  <div v-else class="app-grid">
    <article v-for="a in apps" :key="a.id" class="card app-card">
      <span class="app-icon">{{ a.name[0] }}</span><span class="badge green">有权限</span>
      <h2>{{ a.name }}</h2><p>{{ a.permissionCount }} 项操作已授权</p>
      <p class="muted">系统 → 功能模块 → 功能点 → 操作</p>
      <button class="primary" :disabled="busy" @click="enter(a.id)">进入系统 →</button>
    </article>
  </div>
</template>
