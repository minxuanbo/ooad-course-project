<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { api, canRoute, notify, refreshSession } from '../api.js'
import { errorText, RequestGeneration } from '../lib/presentation.js'
import Modal from '../components/Modal.vue'
import Pagination from '../components/Pagination.vue'
import CollectionEditor from '../components/CollectionEditor.vue'
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
function edit(role = null) {
  editing.value = role || { create: true }
  form.value = { code: role?.code || '', name: role?.name || '', enabled: role?.enabled ?? true }
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
        ? { code: form.value.code.trim(), name: form.value.name.trim() }
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
onMounted(() => load())
onUnmounted(() => generation.cancel())
</script>
<template>
  <section class="card panel">
    <div class="section-heading">
      <div>
        <h2>角色与权限</h2>
        <p class="muted">
          基础角色的授予级别固定。自定义角色默认仅系统管理员可授予，不提高持有人的管理级别。
        </p>
      </div>
      <button v-if="canRoute('POST', '/roles', true)" class="primary" @click="edit()">
        新建角色
      </button>
    </div>
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
      <button class="secondary" :disabled="busy">查询</button>
    </form>
  </section>
  <p v-if="error" class="error card panel" role="alert">
    {{ error }}
    <button class="secondary" @click="load(page)">重试</button>
  </p>
  <section class="card table-scroll">
    <table>
      <thead>
        <tr>
          <th>角色名称 / 编码</th>
          <th>固定管理级别</th>
          <th>角色状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="role in rows" :key="role.id">
          <td>
            {{ role.name }}
            <small>{{ role.code }}</small>
          </td>
          <td>{{ levels[role.managementLevel] || '自定义 · 仅管理员可授予' }}</td>
          <td>
            <span class="badge" :class="role.enabled ? 'green' : 'red'">
              {{ role.enabled ? '启用' : '停用' }}
            </span>
          </td>
          <td>
            <button
              v-if="canRoute('PATCH', '/roles/{id}', true)"
              class="text-button"
              @click="edit(role)"
            >
              编辑定义
            </button>
            <button
              v-if="canRoute('GET', '/roles/{id}/permissions', true)"
              class="text-button"
              @click="assigning = role"
            >
              {{ canRoute('PUT', '/roles/{id}/permissions', true) ? '配置权限' : '查看权限' }}
            </button>
          </td>
        </tr>
        <tr v-if="!rows.length">
          <td colspan="4" class="empty">
            {{ busy ? '正在读取角色…' : error ? '角色读取失败' : '暂无匹配角色' }}
          </td>
        </tr>
      </tbody>
    </table>
    <Pagination :page="page" :size="20" :total="total" :busy="busy" @change="load" />
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
    :readonly="!canRoute('PUT', '/roles/{id}/permissions', true)"
    @close="assigning = null"
    @saved="saved"
  />
</template>
