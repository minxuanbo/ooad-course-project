<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { api, canRoute, notify, refreshSession, session } from '../api.js'
import { errorText, RequestGeneration, organizationRows } from '../lib/presentation.js'
import Modal from '../components/Modal.vue'
import Pagination from '../components/Pagination.vue'
import CollectionEditor from '../components/CollectionEditor.vue'
import MemberList from '../components/MemberList.vue'
import AppIcon from '../components/AppIcon.vue'
import PageActions from '../components/PageActions.vue'
const members = ref(null), departments = ref([]), roleType = ref('')
const typeNames = { SYSTEM_BUILTIN: '系统内置', APPLICATION_PRESET: '应用预置', DEPARTMENT_CUSTOM: '部门自定义', GLOBAL_CUSTOM: '全局自定义' }
const isAdministrator = () => session.me?.user.globalScope && session.me?.user.managementLevel === 5
const canDefine = (role, method, path) => canRoute(method, path, true) && (role?.type !== 'DEPARTMENT_CUSTOM' || isAdministrator())
const rows = ref([]),
  q = ref(''),
  enabled = ref(''),
  page = ref(0),
  total = ref(0),
  busy = ref(false),
  error = ref(''),
  editing = ref(null),
  assigning = ref(null),
  saving = ref(false)
const form = ref({ code: '', name: '', enabled: true }),
  actionError = ref('')
const confirmChange = ref(false)
const actionDenied = ref(false)
const toggling = ref(null)
const generation = new RequestGeneration()
const levels = { 5: '系统管理员', 4: '公司领导', 3: '部门经理', 2: '项目经理', 1: '普通员工' }
/** 列表由数据库搜索、计数与分页，权限定义读取与候选独立。 */
async function load(next = 0) {
  const version = generation.next()
  busy.value = true
  error.value = ''
  rows.value = []
  try {
    const query = new URLSearchParams({ page: next, size: 20, q: q.value.trim() })
    if (enabled.value !== '') query.set('enabled', enabled.value)
    if (roleType.value) query.set('type', roleType.value)
    const result = await api('/roles?' + query)
    if (generation.current(version)) {
      rows.value = result.items
      total.value = result.total
      page.value = next
    }
  } catch (e) {
    if (generation.current(version)) error.value = errorText(e)
  } finally {
    if (generation.current(version)) busy.value = false
  }
}
async function edit(role = null) {
  editing.value = role || { create: true }
  form.value = { code: role?.code || '', name: role?.name || '', enabled: role?.enabled ?? true }
  form.value.type = role?.type || 'GLOBAL_CUSTOM'
  form.value.ownerDepartmentId = role?.ownerDepartmentId || ''
  if (!role && isAdministrator() && canRoute('GET', '/departments/tree')) {
    try { departments.value = organizationRows(await api('/departments/tree')) } catch (e) { actionError.value = errorText(e); return }
  }
  actionError.value = ''
  actionDenied.value = false
  confirmChange.value = false
}
/** 自定义角色只能创建空权限定义，级别不接收用户输入。 */
async function save() {
  if (actionDenied.value || saving.value) return
  if (!editing.value.create && !confirmChange.value) {
    confirmChange.value = true
    return
  }
  saving.value = true
  actionError.value = ''
  try {
    await api(
      editing.value.create ? '/roles' : `/roles/${editing.value.id}`,
      editing.value.create ? 'POST' : 'PATCH',
      editing.value.create
        ? { code: form.value.code.trim(), name: form.value.name.trim(), type: form.value.type, ...(form.value.type === 'DEPARTMENT_CUSTOM' ? { ownerDepartmentId: Number(form.value.ownerDepartmentId) } : {}) }
        : { name: form.value.name.trim(), enabled: form.value.enabled },
    )
    editing.value = null
    notify('角色定义已保存。')
    await refreshSession()
    await load(page.value)
  } catch (e) {
    actionError.value = errorText(e)
    if (e.status === 403) {
      actionDenied.value = true
      await load(page.value)
    }
  } finally {
    saving.value = false
  }
}
async function saved() {
  assigning.value = null
  await refreshSession()
  load(page.value)
}
/** 独立启停入口先展示影响，确认后仅提交状态，不连带覆盖名称。 */
function openToggle(role) {
  if (saving.value || !canDefine(role, 'PATCH', '/roles/{id}')) return
  toggling.value = role
  actionError.value = ''
  actionDenied.value = false
}
async function toggleRole() {
  if (!toggling.value || saving.value || actionDenied.value) return
  saving.value = true
  actionError.value = ''
  try {
    await api(`/roles/${toggling.value.id}`, 'PATCH', { enabled: !toggling.value.enabled })
    toggling.value = null
    notify('角色状态已更新。')
    await refreshSession()
    await load(page.value)
  } catch (e) {
    actionError.value = errorText(e)
    if (e.status === 403) {
      actionDenied.value = true
      await load(page.value)
    }
  } finally {
    saving.value = false
  }
}
function resetFilters() {
  q.value = ''
  enabled.value = ''
  roleType.value = ''
  load(0)
}
onMounted(() => load())
onUnmounted(() => generation.cancel())
</script>
<template>
  <PageActions :busy="busy || saving" @refresh="load(page)"
    ><button
      class="primary role-create"
      :disabled="busy || saving || !canRoute('POST', '/roles', true)"
      :title="!canRoute('POST', '/roles', true) ? '没有新建角色的权限' : ''"
      @click="edit()"
    >
      <AppIcon name="plus" />新建角色
    </button></PageActions
  >
  <section class="card panel">
    <form class="toolbar" @submit.prevent="load(0)">
      <label>
        搜索角色
        <input v-model="q" maxlength="100" placeholder="名称或编码" />
      </label>
      <label>
        角色状态
        <select v-model="enabled">
          <option value="">全部</option>
          <option value="true">启用</option>
          <option value="false">停用</option>
        </select>
      </label>
      <label>角色类型<select v-model="roleType"><option value="">全部类型</option><option v-for="(name, type) in typeNames" :key="type" :value="type">{{ name }}</option></select></label>
      <button class="primary" :disabled="busy">查询</button
      ><button type="button" class="text-button" :disabled="busy" @click="resetFilters">
        重置
      </button>
    </form>
    <p v-if="error" class="error card panel" role="alert">
      {{ error }}
      <button class="secondary" @click="load(page)">重试</button>
    </p>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>角色名称</th>
            <th>角色编码</th>
            <th>来源 / 适用范围</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="role in rows" :key="role.id">
            <td>
              <strong>{{ role.name }}</strong>
            </td>
            <td class="mono">{{ role.code }}</td>
            <td>
              <span class="badge" :class="{ blue: role.managementLevel }">{{
                levels[role.managementLevel] || typeNames[role.type] || '全局自定义'
              }}</span><small v-if="role.ownerDepartmentName" class="role-owner">{{ role.ownerDepartmentName }}及下级</small><small v-else-if="role.applicationName" class="role-owner">{{ role.applicationName }}</small>
            </td>
            <td>
              <span class="role-status" :class="{ off: !role.enabled }">
                {{ role.enabled ? '启用' : '已停用' }}
              </span>
            </td>
            <td>
              <button
                class="text-button"
                :disabled="saving || !canRoute('GET', '/users')"
                :title="!canRoute('GET', '/users') ? '没有查看角色成员的权限' : ''"
                @click="members = role"
              >
                查看成员
              </button>
              <button
                class="text-button"
                :disabled="saving || !canRoute('GET', '/roles/{id}/permissions')"
                :title="!canRoute('GET', '/roles/{id}/permissions') ? '没有读取角色权限的权限' : ''"
                @click="assigning = role"
              >
                {{ canDefine(role, 'PUT', '/roles/{id}/permissions') ? '配置权限' : '查看权限' }}
              </button>
              <button
                class="text-button"
                :disabled="saving || !canDefine(role, 'PATCH', '/roles/{id}')"
                :title="!canDefine(role, 'PATCH', '/roles/{id}') ? '没有维护此角色定义的权限' : ''"
                @click="edit(role)"
              >
                编辑
              </button>
              <button
                class="text-button"
                :class="{ danger: role.enabled }"
                :disabled="saving || !canDefine(role, 'PATCH', '/roles/{id}')"
                :title="!canDefine(role, 'PATCH', '/roles/{id}') ? '没有维护此角色状态的权限' : ''"
                @click="openToggle(role)"
              >
                {{ role.enabled ? '停用' : '启用' }}
              </button>
            </td>
          </tr>
          <tr v-if="!rows.length">
            <td colspan="5" class="empty">
              {{ busy ? '正在读取角色…' : error ? '角色读取失败' : '暂无匹配角色' }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <Pagination :page="page" :size="20" :total="total" :busy="busy" @change="load" />
    <div class="role-footnote">
      <AppIcon
        name="info"
      />授予级别按固定元数据判断，改名不会改变级别。全局及应用角色由管理员授予；部门角色由所属部门及上级获准领导在范围内分配，不改变人员级别。
    </div>
  </section>
  <Modal
    v-if="editing"
    :title="editing.create ? '新建空权限角色' : '编辑角色定义'"
    :busy="saving"
    @close="editing = null"
  >
    <form @submit.prevent="save" @input="confirmChange = false" @change="confirmChange = false">
      <label>
        角色编码
        <input v-model="form.code" required maxlength="100" :disabled="!editing.create" />
      </label>
      <label>
        角色名称
        <input v-model="form.name" required maxlength="100" />
      </label>
      <template v-if="editing.create">
        <label>角色类型<select v-model="form.type"><option value="GLOBAL_CUSTOM">全局自定义</option><option v-if="isAdministrator()" value="DEPARTMENT_CUSTOM">部门自定义</option></select></label>
        <label v-if="form.type === 'DEPARTMENT_CUSTOM'">所属部门<select v-model="form.ownerDepartmentId" required><option value="" disabled>选择所属部门</option><option v-for="department in departments" :key="department.id" :value="department.id">{{ '　'.repeat(department.depth) + department.name }}</option></select></label>
      </template>
      <label v-if="!editing.create" class="checkbox-label">
        <input v-model="form.enabled" type="checkbox" />
        启用角色
      </label>
      <p class="muted">停用角色保留人员关联，但不贡献有效权限，不改变账号自身启用状态。</p>
      <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
      <p v-if="actionDenied" class="error">
        权限或范围已变化，本次操作已禁用；请关闭弹窗并刷新后重新进入。
      </p>
      <p v-if="confirmChange && !actionDenied" class="pending-query" role="alert">
        角色定义为共享配置，改名或启停将影响全部持有者。请核对上方字段后确认保存。
      </p>
      <footer class="dialog-actions">
        <button type="button" class="secondary" :disabled="saving" @click="editing = null">
          取消
        </button>
        <button class="primary" :disabled="actionDenied || saving || !form.name.trim()">
          {{ confirmChange ? '确认保存共享配置' : '保存' }}
        </button>
      </footer>
    </form>
  </Modal>
  <CollectionEditor
    v-if="assigning"
    :key="assigning.id"
    :title="assigning.name + ' · 权限配置'"
    kind="permissions"
    :read-path="`/roles/${assigning.id}/permissions`"
    :save-path="`/roles/${assigning.id}/permissions`"
    :readonly="!canDefine(assigning, 'PUT', '/roles/{id}/permissions')"
    @close="assigning = null"
    @saved="saved"
  />
  <MemberList v-if="members" :key="members.id" :role="members" @close="members = null" />
  <Modal
    v-if="toggling"
    :title="toggling.enabled ? '停用这个角色？' : '启用这个角色？'"
    :busy="saving"
    @close="toggling = null"
    ><strong>{{ toggling.name }}</strong>
    <p>修改影响全部持有该角色的人员，已分配关系会保留。最后完整维护者受保护。</p>
    <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
    <p v-if="actionDenied" class="error">权限或范围已变化，请关闭后重新进入。</p>
    <footer class="dialog-actions">
      <button class="secondary" :disabled="saving" @click="toggling = null">取消</button
      ><button class="primary" :disabled="saving || actionDenied" @click="toggleRole">
        {{ toggling.enabled ? '确认停用' : '确认启用' }}
      </button>
    </footer></Modal
  >
</template>
<style scoped>
.role-owner { display: block; margin-top: 5px; font-size: 11px; color: #8c9db7; }
.panel {
  padding: 0;
}
.toolbar {
  padding: 20px 22px;
  margin: 0;
}
.toolbar label {
  font-size: 11px;
  color: #8c9db7;
}
.toolbar input,
.toolbar select {
  font-size: 12px;
  padding: 10px 12px;
  background: white;
}
.toolbar button {
  font-size: 12px;
  min-height: 38px;
}
.table-scroll {
  margin-top: 0;
}
table {
  min-width: 840px;
}
th {
  background: #f7f9fc;
  font-size: 11px;
  color: #93a3bb;
}
td {
  color: #7b91b0;
  font-size: 12px;
  vertical-align: middle;
}
td strong {
  color: #536f95;
  font-weight: 550;
}
td.mono {
  color: #9aa9bd;
  font-size: 11px;
}
td:last-child {
  white-space: nowrap;
}
td .text-button {
  padding: 5px;
  font-size: 12px;
}
.badge {
  font-size: 10px;
}
.role-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #32a184;
}
.role-status::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}
.role-status.off {
  color: #9aa7b9;
}
.role-footnote {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 17px 22px;
  background: #fafcff;
  border-top: 1px solid #edf1f8;
  font-size: 11px;
  color: #9cabc1;
}
.role-footnote :deep(.ui-icon) {
  width: 14px;
  height: 14px;
  margin-top: 2px;
}
.role-create {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  min-height: 38px;
}
</style>
