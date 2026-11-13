<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { api, notify, session } from '../api.js'
import { errorText, organizationRows, RequestGeneration } from '../lib/presentation.js'
import Modal from '../components/Modal.vue'
const nodes = ref([]), q = ref(''), busy = ref(false), error = ref(''), editing = ref(null), name = ref(''), parentId = ref(''), type = ref(''), saving = ref(false), actionError = ref('')
const deleting = ref(null)
const generation = new RequestGeneration()
const labels = { HEADQUARTERS: '总公司', COMPANY: '分公司', DEPARTMENT: '部门', PROJECT: '项目' }
const children = { HEADQUARTERS: ['COMPANY','DEPARTMENT','PROJECT'], COMPANY: ['DEPARTMENT','PROJECT'], DEPARTMENT: ['PROJECT'], PROJECT: [] }
const rows = computed(() => organizationRows(nodes.value, q.value))
const parents = computed(() => organizationRows(nodes.value).filter(item => item.canCreate))
const childTypes = computed(() => children[nodes.value.find(item => item.id === Number(parentId.value))?.type] || [])
async function load() {
  const version = generation.next(); busy.value = true; error.value = ''; nodes.value = []
  try { const value = await api('/departments'); if (generation.current(version)) nodes.value = value }
  catch (e) { if (generation.current(version)) error.value = errorText(e) }
  finally { if (generation.current(version)) busy.value = false }
}
function create(parent) { editing.value = { create: true }; name.value = ''; parentId.value = String(parent.id); type.value = children[parent.type][0]; actionError.value = '' }
function rename(node) { editing.value = node; name.value = node.name; actionError.value = '' }
async function save() {
  saving.value = true; actionError.value = ''
  try {
    await api(editing.value.create ? '/departments' : `/departments/${editing.value.id}`, editing.value.create ? 'POST' : 'PATCH', editing.value.create ? { parentId: Number(parentId.value), type: type.value, name: name.value.trim() } : { name: name.value.trim() })
    editing.value = null; notify('部门已保存。'); await load()
  } catch (e) { actionError.value = errorText(e) }
  finally { saving.value = false }
}
async function remove(node) {
  saving.value = true; actionError.value = ''
  try { await api(`/departments/${node.id}`, 'DELETE'); notify('空部门已删除。'); deleting.value = null; await load() }
  catch (e) { actionError.value = errorText(e) }
  finally { saving.value = false }
}
onMounted(load)
onUnmounted(() => generation.cancel())
</script>
<template>
  <section class="card panel"><h2>组织部门</h2><p class="muted">新增下级、修改名称及删除空部门。局部管理者只能修改或删除严格下级；项目为叶子节点，不提供层级调整。</p><form class="toolbar" @submit.prevent><label>搜索部门<input v-model="q" placeholder="名称；保留匹配节点的祖先" maxlength="100"></label><button type="button" class="secondary" :disabled="busy || saving" @click="load">刷新目录</button></form></section>
  <p v-if="error || actionError" class="error card panel" role="alert">{{ error || actionError }} <button class="secondary" @click="load">重试</button></p>
  <section class="card table-scroll"><table><thead><tr><th>部门名称</th><th>组织类型</th><th>直属人数</th><th>含下级人数</th><th>操作</th></tr></thead><tbody><tr v-for="node in rows" :key="node.id"><td :style="{ paddingLeft: (20 + node.depth * 24) + 'px' }">{{ node.depth ? '└ ' : '' }}{{ node.name }}<span v-if="node.id === session.me.user.departmentId" class="badge blue">本人部门</span></td><td>{{ labels[node.type] }}</td><td>{{ node.directUsers }}</td><td>{{ node.totalUsers }}</td><td class="row-actions"><button v-if="node.canCreate" class="text-button" :disabled="saving" @click="create(node)">新增下级</button><button v-if="node.canEdit" class="text-button" :disabled="saving" @click="rename(node)">修改名称</button><button v-if="node.canDelete" class="text-button danger" :disabled="saving" @click="deleting = node">删除</button><small v-else-if="node.canEdit">{{ node.totalUsers ? '有人员，不能删除' : '根或有下级，不能删除' }}</small></td></tr><tr v-if="!rows.length"><td colspan="5" class="empty">{{ busy ? '正在读取组织…' : error ? '组织读取失败' : '没有匹配部门' }}</td></tr></tbody></table></section>
  <Modal v-if="editing" :title="editing.create ? '新增下级部门' : '修改部门名称'" :busy="saving" @close="editing = null"><form @submit.prevent="save"><template v-if="editing.create"><label>上级部门<select v-model="parentId" required @change="type = childTypes[0] || ''"><option v-for="parent in parents" :key="parent.id" :value="String(parent.id)">{{ '　'.repeat(parent.depth) + parent.name }}</option></select></label><label>组织类型<select v-model="type" required><option v-for="value in childTypes" :key="value" :value="value">{{ labels[value] }}</option></select></label></template><label>部门名称<input v-model="name" required maxlength="100" placeholder="1至100字符，同一上级下不可重名"></label><p v-if="actionError" class="error" role="alert">{{ actionError }}</p><footer class="dialog-actions"><button type="button" class="secondary" :disabled="saving" @click="editing = null">取消</button><button class="primary" :disabled="saving || !name.trim() || (editing.create && !type)">保存</button></footer></form></Modal>
  <Modal v-if="deleting" title="确认删除空部门" :busy="saving" @close="deleting = null"><p>确定删除“{{ deleting.name }}”？此操作不能恢复，不会删除人员或其他部门。</p><p v-if="actionError" class="error" role="alert">{{ actionError }}</p><footer class="dialog-actions"><button class="secondary" :disabled="saving" @click="deleting = null">取消</button><button class="primary" :disabled="saving" @click="remove(deleting)">确认删除</button></footer></Modal>
</template>
