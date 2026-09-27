<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api.js'
const route = useRoute(), router = useRouter()
const apps = ref([]), status = ref('正在同步…'), result = ref(''), selected = ref(null), error = ref(false)
let timer, syncing = false, alive = true
const count = computed(function() { let n = 0; for (const a of apps.value) if (a.permissionCount > 0) n++; return n })
async function sync() {
  if (syncing) return
  syncing = true
  try {
    const data = await api('/me/applications')
    if (!alive) return
    apps.value = data
    status.value = '权限已同步 · ' + new Date().toLocaleTimeString('zh-CN')
    if (selected.value !== null) {
      for (const a of data) if (a.id === selected.value) {
        error.value = !a.permissionCount
        result.value = a.permissionCount ? '您有权限访问所选系统，但系统暂未接入。' : '您已失去所选系统的访问权限，授权可能被回收或系统已停用。'
      }
    }
  } catch { status.value = '同步失败，当前显示可能已过期；进入系统仍需在线校验。' }
  finally { syncing = false }
}
async function enter(id) {
  selected.value = id
  try { const data = await api('/me/applications/' + id + '/enter', 'POST'); if(selected.value === id) { result.value = data.message; error.value = false } }
  catch (e) { if(selected.value === id) { result.value = e.message; error.value = true } }
}
onMounted(async function() {
  await sync()
  if (!alive) return
  if (route.query.initial) {
    await router.replace('/portal')
    if (count.value === 1) for (const a of apps.value) if (a.permissionCount > 0) await enter(a.id)
  }
  if (!alive) return
  timer = setInterval(sync, 3000); window.addEventListener('focus', sync)
})
onUnmounted(function() { alive = false; clearInterval(timer); window.removeEventListener('focus', sync) })
</script>
<template>
  <div class="welcome"><div><span class="pill">我的应用</span><h2>{{ count }} 个系统有权限访问</h2><p>选择业务系统。权限每 3 秒同步，进入时实时校验。</p></div></div>
  <div v-if="result" class="card access-result" :class="{ error }" role="status">{{ result }}</div>
  <p class="muted">{{ status }}</p>
  <div class="app-grid"><article v-for="a in apps" :key="a.id" class="card app-card" :class="{ unavailable: !a.permissionCount }"><span class="app-icon">{{ a.name[0] }}</span><span class="badge" :class="a.permissionCount ? 'green' : 'red'">{{ a.permissionCount ? '有权限' : '无权限' }}</span><h2>{{ a.name }}</h2><p>{{ a.permissionCount ? '已获授权 ' + a.permissionCount + ' 项功能' : '未获授权、授权已回收或系统已停用' }}</p><p class="muted">暂未接入</p><button class="secondary" @click="enter(a.id)">{{ a.permissionCount ? '进入系统 →' : '检查访问权限' }}</button></article></div>
</template>
