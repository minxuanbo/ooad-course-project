<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { api, canRoute, notify } from '../api.js'
import { errorText, organizationRows, RequestGeneration } from '../lib/presentation.js'
import Modal from '../components/Modal.vue'
import Pagination from '../components/Pagination.vue'
import CollectionEditor from '../components/CollectionEditor.vue'
const rows = ref([]), departments = ref([]), total = ref(0), page = ref(0), size = ref(20), busy = ref(false), error = ref('')
const filters = ref({ q: '', departmentId: '', enabled: '' }), editing = ref(null), moving = ref(null), moveTo = ref(''), saving = ref(false), actionError = ref('')
const disabling = ref(null)
const generation = new RequestGeneration()
/** 人员页独立重读部门树，树读取失败不展示错误的全公司筛选。 */
async function load(next = 0) {
  const version = generation.next(); busy.value = true; error.value = ''; rows.value = []
  try {
    const tree = await api('/departments/tree')
    const params = new URLSearchParams({ page: next, size: size.value })
    for (const [key, value] of Object.entries(filters.value)) if (value !== '') params.set(key, value.trim())
    const result = await api('/users?' + params)
    if (generation.current(version)) { departments.value = organizationRows(tree); rows.value = result.items; total.value = result.total; page.value = next }
  } catch (e) { if (generation.current(version)) error.value = errorText(e) }
  finally { if (generation.current(version)) busy.value = false }
}
async function disable(user) {
  saving.value = true; actionError.value = ''
  try { await api(`/users/${user.id}/status`, 'PATCH', { enabled: false }); notify('账号已停用。'); disabling.value = null; await load(page.value) }
  catch (e) { actionError.value = errorText(e) }
  finally { saving.value = false }
}
async function move() {
  saving.value = true; actionError.value = ''
  try { await api(`/users/${moving.value.id}/department`, 'PUT', { departmentId: Number(moveTo.value) }); moving.value = null; notify('部门归属已更新，职位保持来源资料。'); await load(page.value) }
  catch (e) { actionError.value = errorText(e) }
  finally { saving.value = false }
}
function editRoles(user) { editing.value = user }
function openMove(user) { moving.value = user; moveTo.value = String(user.departmentId); actionError.value = '' }
function saved() { editing.value = null; load(page.value) }
onMounted(() => load())
onUnmounted(() => generation.cancel())
</script>
<template>
  <section class="card"><form class="toolbar user-filters" @submit.prevent="load(0)"><label>搜索人员<input v-model="filters.q" maxlength="100" placeholder="姓名或工号"></label><label>部门及下级<select v-model="filters.departmentId"><option value="">全部管理范围</option><option v-for="item in departments" :key="item.id" :value="String(item.id)">{{ '　'.repeat(item.depth) + item.name }}</option></select></label><label>账号状态<select v-model="filters.enabled"><option value="">全部</option><option value="true">启用</option><option value="false">停用</option></select></label><button class="primary" :disabled="busy || saving">查询</button><button type="button" class="secondary" :disabled="busy || saving" @click="filters = { q: '', departmentId: '', enabled: '' }; load(0)">重置</button></form></section>
  <p v-if="error || actionError" class="error card panel" role="alert">{{ error || actionError }} <button class="secondary" :disabled="busy" @click="load(page)">重试</button></p>
  <p class="muted">仅能维护范围内级别严格低于本人的人员；本人、同级和上级只读。职位来自员工资料。</p>
  <section class="card table-scroll"><table><thead><tr><th>姓名 / 工号</th><th>所属部门</th><th>职位</th><th>当前角色</th><th>账号状态</th><th>操作</th></tr></thead><tbody><tr v-for="user in rows" :key="user.id"><td>{{ user.name }}<small>{{ user.username }}</small></td><td>{{ user.departmentName }}</td><td>{{ user.position || '未提供' }}</td><td><span v-for="role in user.roles" :key="role.id" class="badge" :class="role.enabled ? 'blue' : 'red'">{{ role.name }}{{ role.enabled ? '' : ' · 停用' }}</span><span v-if="!user.roles.length" class="muted">未授予角色</span></td><td><span class="badge" :class="user.enabled ? 'green' : 'red'">{{ user.enabled ? '启用' : '停用' }}</span></td><td class="row-actions"><button class="text-button" :disabled="saving" @click="editRoles(user)">{{ user.writable && canRoute('PUT','/users/{id}/roles') ? '调整角色' : '查看角色' }}</button><button v-if="user.writable && canRoute('PUT','/users/{id}/department')" class="text-button" :disabled="saving" @click="openMove(user)">调动归属</button><button v-if="user.writable && user.enabled && canRoute('PATCH','/users/{id}/status')" class="text-button danger" :disabled="saving" @click="disabling = user; actionError = ''">停用</button></td></tr><tr v-if="!rows.length"><td colspan="6" class="empty">{{ busy ? '正在读取人员…' : error ? '人员读取失败' : '没有符合条件的人员' }}</td></tr></tbody></table><Pagination :page="page" :size="size" :total="total" :busy="busy || saving" @change="load" /></section>
  <CollectionEditor v-if="editing" :key="editing.id" :title="editing.name + ' · 当前角色'" kind="roles" :read-path="`/users/${editing.id}/roles`" :save-path="`/users/${editing.id}/roles`" :readonly="!editing.writable || !canRoute('PUT','/users/{id}/roles')" @close="editing = null" @saved="saved" />
  <Modal v-if="moving" title="调动部门归属" :busy="saving" @close="moving = null"><p>{{ moving.name }} · {{ moving.username }}</p><p class="muted">仅修改权限系统内的部门归属，不修改职位。</p><form @submit.prevent="move"><label>目标部门<select v-model="moveTo" required><option v-for="item in departments" :key="item.id" :value="String(item.id)">{{ '　'.repeat(item.depth) + item.name }}</option></select></label><p v-if="actionError" class="error" role="alert">{{ actionError }}</p><footer class="dialog-actions"><button type="button" class="secondary" :disabled="saving" @click="moving = null">取消</button><button class="primary" :disabled="saving || !moveTo">确认调动</button></footer></form></Modal>
  <Modal v-if="disabling" title="确认停用账号" :busy="saving" @close="disabling = null"><p>停用 {{ disabling.name }}（{{ disabling.username }}）？现有管理会话随后失效，部门和角色关联保留。</p><p v-if="actionError" class="error" role="alert">{{ actionError }}</p><footer class="dialog-actions"><button class="secondary" :disabled="saving" @click="disabling = null">取消</button><button class="primary" :disabled="saving" @click="disable(disabling)">确认停用</button></footer></Modal>
</template>
