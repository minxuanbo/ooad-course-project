<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, notify } from '../api.js'
const route = useRoute(), router = useRouter(), id = Number(route.params.id)
const application = ref(null), modules = ref([]), features = ref([]), selected = ref(null)
const status = ref('正在校验系统权限…'), busy = ref(false), ready = ref(false)
let timer, alive = true, syncing = false, selectionVersion = 0
function clearDirectory() { application.value = null; modules.value = []; features.value = []; ready.value = false }
async function failure(error) {
  if (!alive) return
  clearDirectory(); status.value = error.message
  if (error.status === 403 || error.status === 404) {
    notify('系统或模块已不可访问，已返回我的应用。')
    await router.replace('/portal')
  }
}
async function sync() {
  if (syncing || !alive || busy.value) return
  syncing = true
  const version = selectionVersion, moduleId = selected.value
  try {
    // Direct navigation also requires entry authorization; polling only reads directories.
    let app = application.value
    if (!app) {
      const result = await api('/me/applications/' + id + '/enter', 'POST')
      if (!result.allowed) { const e = Error('无系统权限'); e.status = 403; throw e }
      app = result.application
    }
    const catalog = await api('/me/applications/' + id + '/modules')
    let moduleExists = moduleId === null
    for (const m of catalog.modules) if (m.id === moduleId) moduleExists = true
    let list = []
    if (moduleId !== null && moduleExists) {
      const result = await api('/me/applications/' + id + '/modules/' + moduleId + '/features')
      list = result.features
    }
    if (!alive || version !== selectionVersion) return
    application.value = app; modules.value = catalog.modules; features.value = list
    if (!moduleExists) { selected.value = null; notify('该模块已停用或移除，请重新选择。') }
    ready.value = true; status.value = '权限已同步 · ' + new Date().toLocaleTimeString('zh-CN')
  } catch (error) { if (version === selectionVersion) await failure(error) }
  finally {
    syncing = false
    if (alive && !busy.value && version !== selectionVersion) sync()
  }
}
function selectModule(id) {
  selected.value = id; features.value = []; ready.value = false; selectionVersion++; sync()
}
async function operate(operation, feature) {
  if (!ready.value || busy.value || !operation.allowed || !application.value) return
  busy.value = true; selectionVersion++
  try {
    const result = await api('/authorize', 'POST', {
      appCode: application.value.code, permissionCode: operation.permissionCode,
      requestId: 'ui-' + crypto.randomUUID()
    })
    if (!alive) return
    if (!result.allowed) { operation.allowed = false; ready.value = false; features.value = [] }
    notify((result.allowed ? '鉴权通过：' : '鉴权拒绝：') + feature.name + ' / ' + operation.label + '。仅检查权限，未执行业务操作。')
    busy.value = false
    await sync()
  } catch (error) { await failure(error) }
  finally { busy.value = false }
}
onMounted(function() { sync(); timer = setInterval(sync, 3000); window.addEventListener('focus', sync) })
onUnmounted(function() { alive = false; selectionVersion++; clearInterval(timer); window.removeEventListener('focus', sync) })
</script>
<template>
  <div class="system-heading">
    <RouterLink class="back-to-apps" to="/portal" aria-label="返回我的应用">
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="m14 6-6 6 6 6M8 12h12" /></svg>
      <span>我的应用</span>
    </RouterLink>
    <h2 v-if="application">{{ application.name }}</h2>
    <button class="secondary" @click="sync">刷新权限</button>
  </div>
  <p class="muted" role="status">{{ status }}</p>
  <p class="prototype-hint">功能操作仅演示真实鉴权，不执行业务增删改、审批或导出。每次点击重新校验，日志由管理员查看。</p>
  <template v-if="application">
    <h2>功能模块 <small>选择模块查看功能点</small></h2>
    <div class="module-grid"><button v-for="m in modules" :key="m.id" class="module-tile" :class="{ selected: selected === m.id }" @click="selectModule(m.id)"><strong>{{ m.name }}</strong><small>{{ m.grantedPermissionCount }} / {{ m.permissionCount }} 项操作有权限</small></button></div>
    <p v-if="!modules.length" class="empty">暂无可用模块</p>
    <p v-else-if="selected === null" class="empty">请选择上方功能模块</p>
    <p v-else-if="!ready" class="empty">正在重新确认权限…</p>
    <p v-else-if="!features.length" class="empty">该模块暂无可用功能点</p>
    <article v-for="f in features" :key="f.id" class="card feature-card">
      <h3>{{ f.name }}</h3><p class="muted">{{ f.description }}</p>
      <div class="operation-list"><button v-for="operation in f.operations" :key="operation.permissionId" class="operation" :class="{ granted: operation.allowed }" :disabled="!ready || busy || !operation.allowed" :title="operation.allowed ? '点击后再次鉴权' : '无此操作权限'" @click="operate(operation, f)">{{ operation.label }}{{ operation.allowed ? '' : ' · 无权限' }}</button></div>
      <p v-if="!f.operations.length" class="muted">暂无已登记的有效操作</p>
    </article>
  </template>
</template>
<style scoped>
.system-heading { gap: 16px; margin-bottom: 16px; }
.system-heading h2 { margin: 0; }
.back-to-apps {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 40px;
  padding: 9px 14px;
  border: 1px solid #dce5f3;
  border-radius: 10px;
  background: #fff;
  color: #536b91;
  font-size: 13px;
  font-weight: 500;
  line-height: 20px;
  text-decoration: none;
  white-space: nowrap;
  box-shadow: 0 1px 2px #24334a05;
  transition: background-color .16s, border-color .16s, color .16s;
}
.back-to-apps svg { width: 18px; height: 18px; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
.back-to-apps:hover { background: #edf3ff; border-color: #b9cdf5; color: #2859cd; }
.back-to-apps:active { background: #e1ebff; }
.back-to-apps:focus-visible { outline: 3px solid #93b8ff; outline-offset: 3px; }
</style>
