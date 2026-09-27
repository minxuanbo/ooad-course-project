<script setup>
import { ref, onMounted } from 'vue'
import { api, employmentLabel } from '../api.js'
import RoleEditor from '../components/RoleEditor.vue'
import Handover from '../components/Handover.vue'
const q = ref(''), company = ref(''), departmentId = ref(''), page = ref(0)
const companies = ref([]), departments = ref([]), items = ref([]), total = ref(0), busy = ref(false), error = ref('')
const editor = ref(null), handover = ref(null)
let request = 0, filterRequest = 0
async function load() {
  const id = ++request
  busy.value = true; error.value = ''
  const params = new URLSearchParams({ q: q.value.trim(), company: company.value, page: page.value, size: 20 })
  if (departmentId.value) params.set('departmentId', departmentId.value)
  try {
    const data = await api('/users/page?' + params)
    if (id !== request) return
    items.value = data.items; total.value = data.total
  } catch (e) { if (id === request) { error.value = e.message; items.value = []; total.value = 0 } }
  finally { if (id === request) busy.value = false }
}
async function loadFilters() {
  const id = ++filterRequest
  try {
    const data = await api('/users/filters?company=' + encodeURIComponent(company.value))
    if (id !== filterRequest) return
    companies.value = data.companies; departments.value = data.departments
  } catch (e) { if (id === filterRequest) error.value = e.message }
}
async function companyChanged() {
  departmentId.value = ''; departments.value = []; page.value = 0
  await Promise.all([load(), loadFilters()])
}
function search() { page.value = 0; load() }
async function reset() { q.value = ''; company.value = ''; await companyChanged() }
function turn(delta) { page.value += delta; load() }
function saved() { editor.value = null; handover.value = null; load() }
onMounted(function() { load(); loadFilters() })
</script>
<template>
  <section class="card">
    <form class="toolbar user-filters" @submit.prevent="search">
      <label>所属公司<select v-model="company" aria-label="所属公司" @change="companyChanged"><option value="">全部公司</option><option v-for="c in companies" :key="c.company" :value="c.company">{{ c.company }}</option></select></label>
      <label>所属部门<select v-model="departmentId" aria-label="所属部门" @change="search"><option value="">全部部门</option><option v-for="d in departments" :key="d.id" :value="d.id">{{ d.name }}</option></select></label>
      <label class="keyword">员工搜索<input v-model="q" aria-label="搜索用户" placeholder="姓名、工号或手机号"></label>
      <button class="primary" :disabled="busy">查询</button><button type="button" class="secondary" @click="reset">重置</button>
    </form>
    <p v-if="error" class="error panel" role="alert">{{ error }}</p>
    <div class="table-scroll" :aria-busy="busy"><table><thead><tr><th>员工</th><th>工号</th><th>电话</th><th>所属组织</th><th>岗位</th><th>当前角色</th><th>账号／人员状态</th><th>操作</th></tr></thead>
      <tbody><tr v-if="busy"><td colspan="8" class="empty">正在查询…</td></tr>
      <template v-else><tr v-for="u in items" :key="u.id"><td><strong>{{ u.name }}</strong></td><td class="mono">{{ u.employeeNo }}</td><td class="mono nowrap">{{ u.phone || '—' }}</td><td>{{ u.company || '—' }}<small>{{ u.department || '—' }}</small></td><td>{{ u.position }}</td><td>{{ u.roles || '未分配角色' }}</td><td><span class="badge" :class="{ green: u.status === 'ACTIVE' }">{{ u.status === 'ACTIVE' ? '已启用' : '已停用' }}</span><small>{{ employmentLabel(u.employmentStatus) }}</small></td><td class="nowrap"><button class="text-button" :disabled="u.employmentStatus === 'DEPARTED'" @click="editor = u">分配角色</button><button class="text-button" @click="handover = u">离职交接</button></td></tr>
      <tr v-if="!items.length"><td colspan="8" class="empty">未找到匹配的用户</td></tr></template></tbody>
    </table></div>
    <div class="pagination"><span>共 {{ total }} 人 · 第 {{ page + 1 }} / {{ Math.max(1, Math.ceil(total / 20)) }} 页</span><div><button class="secondary" :disabled="busy || page === 0" @click="turn(-1)">上一页</button> <button class="secondary" :disabled="busy || (page + 1) * 20 >= total" @click="turn(1)">下一页</button></div></div>
  </section>
  <RoleEditor v-if="editor" kind="users" :target="editor" @close="editor = null" @saved="saved" />
  <Handover v-if="handover" :target="handover" @close="handover = null" @saved="saved" />
</template>
