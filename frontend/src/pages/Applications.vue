<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { api, canRoute, notify, refreshSession, session } from '../api.js'
import { errorText, RequestGeneration } from '../lib/presentation.js'
import PageActions from '../components/PageActions.vue'
import Pagination from '../components/Pagination.vue'
import Modal from '../components/Modal.vue'
import AppIcon from '../components/AppIcon.vue'

const router = useRouter()
const rows = ref([]), page = ref(0), total = ref(0), q = ref(''), enabled = ref('')
const busy = ref(false), saving = ref(false), error = ref(''), actionError = ref('')
const editing = ref(null), changing = ref(null), actionDenied = ref(false)
const form = ref({ code: '', name: '', connectionStatus: 'PENDING' })
const generation = new RequestGeneration()
const may = (method, path) => canRoute(method, path, true)

/** 登记目录由数据库筛选计数并分页；晚响应不能覆盖已关闭页面。 */
async function load(next = 0) {
  const version = generation.next()
  busy.value = true
  error.value = ''
  rows.value = []
  const query = new URLSearchParams({ page: next, size: 20, q: q.value.trim() })
  if (enabled.value !== '') query.set('enabled', enabled.value)
  try {
    const result = await api('/systems/page?' + query)
    if (!generation.current(version)) return
    rows.value = result.items
    total.value = result.total
    page.value = next
  } catch (e) {
    if (generation.current(version)) error.value = errorText(e)
  } finally {
    if (generation.current(version)) busy.value = false
  }
}

/** 编码创建后只读；接入状态与可用性由显式操作分别维护。 */
function edit(system = null) {
  if (saving.value || !may(system ? 'PATCH' : 'POST', system ? '/systems/{id}' : '/systems')) return
  editing.value = system || { create: true }
  form.value = { code: system?.code || '', name: system?.name || '', connectionStatus: system?.connectionStatus || 'PENDING' }
  actionError.value = ''
  actionDenied.value = false
}

/** 新登记仅为待接入，修改不会建立业务接口或给任何角色自动选权。 */
async function save() {
  if (saving.value || actionDenied.value) return
  saving.value = true
  actionError.value = ''
  try {
    await api(editing.value.create ? '/systems' : '/systems/' + editing.value.id,
      editing.value.create ? 'POST' : 'PATCH', editing.value.create
        ? { code: form.value.code.trim(), name: form.value.name.trim() }
        : { name: form.value.name.trim(), connectionStatus: form.value.connectionStatus })
    editing.value = null
    notify('应用资料已保存，授权仍需单独配置。')
    await refreshSession()
    await load(page.value)
  } catch (e) {
    actionError.value = errorText(e)
    if (e.status === 403) actionDenied.value = true
  } finally {
    saving.value = false
  }
}

/** 移除与恢复均先显示影响，不能将目录行误当成可无痕删除的临时数据。 */
function change(system) {
  if (saving.value || !may(system.enabled ? 'DELETE' : 'PATCH', '/systems/{id}')) return
  changing.value = system
  actionError.value = ''
  actionDenied.value = false
}

/** 停用接入立即影响下一请求，恢复使用原编号和现存关系。 */
async function confirmChange() {
  if (saving.value || actionDenied.value) return
  saving.value = true
  actionError.value = ''
  try {
    await api('/systems/' + changing.value.id, changing.value.enabled ? 'DELETE' : 'PATCH',
      changing.value.enabled ? undefined : { enabled: true })
    changing.value = null
    notify('应用可用状态已更新。')
    await refreshSession()
    await load(page.value)
  } catch (e) {
    actionError.value = errorText(e)
    if (e.status === 403) actionDenied.value = true
  } finally {
    saving.value = false
  }
}

onMounted(() => load())
onUnmounted(() => generation.cancel())
</script>
<template>
  <PageActions :busy="busy || saving" @refresh="load(page)">
    <button class="primary icon-label" :disabled="busy || saving || !may('POST', '/systems')"
      :title="!may('POST', '/systems') ? '没有新增应用的权限' : ''" @click="edit()">
      <AppIcon name="plus" />新增应用
    </button>
  </PageActions>
  <section class="card panel">
    <form class="toolbar" @submit.prevent="load(0)">
      <label>搜索应用<input v-model="q" maxlength="100" placeholder="系统名称或编码" /></label>
      <label>管理状态<select v-model="enabled"><option value="">全部</option><option value="true">受管中</option><option value="false">已移除</option></select></label>
      <button class="primary" :disabled="busy || saving">查询</button>
    </form>
    <p class="muted">新增应用默认待接入；移除会停止访问并保留目录、原有授权关系和历史日志。接入业务仍需配置真实权限与接口。</p>
    <p v-if="error" class="error" role="alert">{{ error }}<button class="secondary" :disabled="busy" @click="load(page)">重试</button></p>
    <div class="table-scroll"><table>
      <thead><tr><th>系统名称</th><th>稳定编码</th><th>接入状态</th><th>管理状态</th><th>操作</th></tr></thead>
      <tbody><tr v-for="system in rows" :key="system.id">
        <td>{{ system.name }}</td><td class="mono">{{ system.code }}</td>
        <td><span class="badge" :class="system.connectionStatus === 'CONNECTED' ? 'green' : 'blue'">{{ system.connectionStatus === 'CONNECTED' ? '已接入' : '待接入' }}</span></td>
        <td>{{ system.enabled ? '受管中' : '已移除' }}</td>
        <td class="row-actions"><button class="text-button" :disabled="busy || saving || system.code === 'rbac' || session.me?.user.managementLevel !== 5 || !may('GET', '/systems/{id}/onboarding')" :title="system.code === 'rbac' ? 'RBAC自身通过资源与API维护' : '查看或配置该应用接入'" @click="router.push('/applications/' + system.id + '/onboarding')">配置接入</button><button class="text-button" :disabled="saving || !may('PATCH', '/systems/{id}')" title="需具备应用资料维护权限" @click="edit(system)">编辑</button>
          <button class="text-button" :class="{ danger: system.enabled }" :disabled="saving || !may(system.enabled ? 'DELETE' : 'PATCH', '/systems/{id}')" title="需具备对应移除或恢复权限" @click="change(system)">{{ system.enabled ? '移除' : '恢复' }}</button></td>
      </tr><tr v-if="!rows.length"><td colspan="5" class="empty">{{ busy ? '正在读取应用…' : error ? '应用读取失败' : '暂无匹配应用' }}</td></tr></tbody>
    </table></div>
    <Pagination :page="page" :size="20" :total="total" :busy="busy || saving" @change="load" />
  </section>
  <Modal v-if="editing" :title="editing.create ? '新增受管应用' : '编辑应用资料'" :busy="saving" @close="editing = null">
    <form @submit.prevent="save">
      <label>系统编码<input v-model="form.code" required maxlength="100" pattern="[a-z0-9][a-z0-9_-]*" :disabled="!editing.create || saving" /></label>
      <label>系统名称<input v-model="form.name" required maxlength="191" :disabled="saving" /></label>
      <label v-if="!editing.create">接入状态<select v-model="form.connectionStatus" :disabled="saving"><option value="PENDING">待接入</option><option value="CONNECTED">已接入</option></select></label>
      <p class="muted">稳定编码创建后不可修改。标记已接入不会自动实现业务接口或授予业务权限。</p>
      <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
      <footer class="dialog-actions"><button type="button" class="secondary" :disabled="saving" @click="editing = null">取消</button><button class="primary" :disabled="saving || actionDenied || !form.name.trim()">保存</button></footer>
    </form>
  </Modal>
  <Modal v-if="changing" :title="changing.enabled ? '移除受管应用' : '恢复受管应用'" :busy="saving" @close="changing = null">
    <p>{{ changing.name }} · {{ changing.code }}</p>
    <p>{{ changing.enabled ? '将停用此应用接入，后续身份和权限请求立即拒绝。保留应用编号、原有授权关系和历史日志，可由获准人员恢复。' : '将恢复该应用，仍按现有角色、权限及接入状态鉴权，不恢复曾被人工撤销的授权。' }}</p>
    <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
    <footer class="dialog-actions"><button class="secondary" :disabled="saving" @click="changing = null">取消</button><button class="primary" :disabled="saving || actionDenied" @click="confirmChange">{{ changing.enabled ? '确认移除' : '确认恢复' }}</button></footer>
  </Modal>
</template>
