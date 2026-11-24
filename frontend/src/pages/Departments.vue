<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { api, canRoute, notify, session } from '../api.js'
import { errorText, organizationRows, RequestGeneration } from '../lib/presentation.js'
import Modal from '../components/Modal.vue'
import MemberList from '../components/MemberList.vue'
import AppIcon from '../components/AppIcon.vue'
import PageActions from '../components/PageActions.vue'
const members = ref(null)
const nodes = ref([]),
  q = ref(''),
  busy = ref(false),
  error = ref(''),
  editing = ref(null),
  name = ref(''),
  parentId = ref(''),
  type = ref(''),
  saving = ref(false),
  actionError = ref('')
const deleting = ref(null)
const actionDenied = ref(false)
const generation = new RequestGeneration()
const selectedId = ref(null)
const expanded = ref(new Set())
let initialized = false
const labels = { HEADQUARTERS: '总公司', COMPANY: '分公司', DEPARTMENT: '部门', PROJECT: '项目' }
const children = {
  HEADQUARTERS: ['COMPANY', 'DEPARTMENT', 'PROJECT'],
  COMPANY: ['DEPARTMENT', 'PROJECT'],
  DEPARTMENT: ['PROJECT'],
  PROJECT: [],
}
const selected = computed(() => nodes.value.find((node) => node.id === selectedId.value) || null)
const directChildren = computed(() =>
  nodes.value.filter((node) => node.parentId === selected.value?.id),
)
const breadcrumbs = computed(() => {
  const path = []
  for (
    let node = selected.value;
    node;
    node = nodes.value.find((item) => item.id === node.parentId)
  )
    path.unshift(node)
  return path
})
/** 搜索保留可见祖先并临时展开命中路径，清空后恢复原有折叠状态。 */
const treeRows = computed(() =>
  organizationRows(nodes.value, q.value).filter((node) => {
    if (q.value.trim()) return true
    for (
      let parent = nodes.value.find((item) => item.id === node.parentId);
      parent;
      parent = nodes.value.find((item) => item.id === parent.parentId)
    ) {
      if (!expanded.value.has(parent.id)) return false
    }
    return true
  }),
)
const matchCount = computed(
  () =>
    nodes.value.filter((node) => node.name.toLowerCase().includes(q.value.trim().toLowerCase()))
      .length,
)
const parents = computed(() => organizationRows(nodes.value).filter((item) => item.canCreate))
const childTypes = computed(
  () => children[nodes.value.find((item) => item.id === Number(parentId.value))?.type] || [],
)
/** 组织响应只用于发起请求的身份与部门范围，不回填已卸载或较旧的请求。 */
function identityKey() {
  return JSON.stringify([
    session.token,
    session.me?.user.departmentId,
    canRoute('GET', '/departments'),
  ])
}
async function load() {
  const version = generation.next()
  const identity = identityKey()
  busy.value = true
  error.value = ''
  nodes.value = []
  try {
    const value = await api('/departments')
    if (generation.current(version) && identity === identityKey()) {
      nodes.value = value
      if (!value.some((node) => node.id === selectedId.value))
        selectedId.value = value[0]?.id ?? null
      if (!initialized) {
        expanded.value = new Set(
          organizationRows(value)
            .filter((node) => node.depth < 2)
            .map((node) => node.id),
        )
        initialized = true
      }
    }
  } catch (e) {
    if (generation.current(version) && identity === identityKey()) error.value = errorText(e)
  } finally {
    if (generation.current(version)) busy.value = false
  }
}
/** 切换当前部门并展开其祖先，右侧保持直接下级口径，不发起额外查询。 */
function selectDepartment(node) {
  if (busy.value || saving.value || !nodes.value.some((item) => item.id === node?.id)) return
  selectedId.value = node.id
  for (const ancestor of breadcrumbs.value.slice(0, -1)) expanded.value.add(ancestor.id)
}
function toggleDepartment(id) {
  if (expanded.value.has(id)) expanded.value.delete(id)
  else expanded.value.add(id)
}
function expandAll() {
  q.value = ''
  expanded.value = new Set(nodes.value.map((node) => node.id))
}
function hasChildren(node) {
  return nodes.value.some((item) => item.parentId === node.id)
}
function departmentIcon(node) {
  return ['HEADQUARTERS', 'COMPANY'].includes(node.type)
    ? 'building'
    : node.type === 'PROJECT'
      ? 'project'
      : 'folder'
}
/** 服务端可执行标记决定维护入口，末级项目不能创建下级。 */
function create(parent) {
  if (busy.value || saving.value || !parent?.canCreate || !children[parent.type]?.length) return
  editing.value = { create: true }
  name.value = ''
  parentId.value = String(parent.id)
  type.value = children[parent.type][0]
  actionError.value = ''
  actionDenied.value = false
}
function rename(node) {
  if (busy.value || saving.value || !node?.canEdit) return
  editing.value = node
  name.value = node.name
  actionError.value = ''
  actionDenied.value = false
}
async function save() {
  if (actionDenied.value || saving.value) return
  saving.value = true
  actionError.value = ''
  try {
    const createdParent = editing.value.create ? Number(parentId.value) : null
    await api(
      editing.value.create ? '/departments' : `/departments/${editing.value.id}`,
      editing.value.create ? 'POST' : 'PATCH',
      editing.value.create
        ? { parentId: Number(parentId.value), type: type.value, name: name.value.trim() }
        : { name: name.value.trim() },
    )
    editing.value = null
    if (createdParent !== null) selectedId.value = createdParent
    notify('部门已保存。')
    await load()
  } catch (e) {
    actionError.value = errorText(e)
    if (e.status === 403) {
      actionDenied.value = true
      await load()
    }
  } finally {
    saving.value = false
  }
}
async function remove(node) {
  if (actionDenied.value || saving.value) return
  saving.value = true
  actionError.value = ''
  try {
    await api(`/departments/${node.id}`, 'DELETE')
    notify('空部门已删除。')
    deleting.value = null
    if (selectedId.value === node.id) selectedId.value = node.parentId
    await load()
  } catch (e) {
    actionError.value = errorText(e)
    if (e.status === 403) {
      actionDenied.value = true
      await load()
    }
  } finally {
    saving.value = false
  }
}
/** 删除确认独立于原生浏览器提示，关闭确认不改变数据。 */
function openDelete(node) {
  if (busy.value || saving.value || !node?.canDelete) return
  deleting.value = node
  actionError.value = ''
  actionDenied.value = false
}
/** 能力或部门范围变化时清除旧目录与成员弹窗，随后读取当前可见范围。 */
watch(identityKey, () => {
  generation.cancel()
  nodes.value = []
  selectedId.value = null
  expanded.value = new Set()
  initialized = false
  members.value = null
  if (session.me && canRoute('GET', '/departments')) load()
  else busy.value = false
})
onMounted(load)
onUnmounted(() => generation.cancel())
</script>
<template>
  <PageActions :busy="busy || saving" @refresh="load" />
  <p v-if="error" class="error card panel" role="alert">
    {{ error }}<button class="secondary" :disabled="busy || saving" @click="load">重试</button>
  </p>
  <div class="department-layout">
    <section class="card department-directory" aria-labelledby="department-directory-title">
      <div class="directory-heading">
        <h2 id="department-directory-title">部门目录</h2>
        <span class="directory-count">{{ nodes.length }}</span>
      </div>
      <div class="directory-search">
        <AppIcon name="search" /><input
          v-model="q"
          aria-label="搜索部门名称"
          placeholder="搜索部门名称"
          maxlength="100"
        />
      </div>
      <div class="directory-caption">
        <span>{{ q.trim() ? '匹配 ' + matchCount + ' 个部门' : '当前可见范围' }}</span
        ><button class="text-button" :disabled="busy" @click="expandAll">
          {{ q.trim() ? '清除搜索' : '展开全部' }}
        </button>
      </div>
      <ul class="department-tree" aria-label="部门目录节点">
        <li
          v-for="node in treeRows"
          :key="node.id"
          :style="{ paddingLeft: node.depth * 18 + 'px' }"
        >
          <div class="department-tree-row" :class="{ selected: selectedId === node.id }">
            <button
              v-if="hasChildren(node)"
              class="department-toggle"
              :class="{ expanded: q.trim() || expanded.has(node.id) }"
              :aria-label="(q.trim() || expanded.has(node.id) ? '折叠' : '展开') + node.name"
              :aria-expanded="!!q.trim() || expanded.has(node.id)"
              :disabled="!!q.trim() || saving"
              @click="toggleDepartment(node.id)"
            >
              <AppIcon name="chevron" />
            </button>
            <span v-else class="department-toggle-spacer"></span>
            <button
              class="department-select"
              :aria-current="selectedId === node.id ? 'true' : undefined"
              :disabled="saving"
              @click="selectDepartment(node)"
            >
              <AppIcon :name="departmentIcon(node)" /><span>{{ node.name }}</span
              ><small title="含下级总人数">{{ node.totalUsers }}</small>
            </button>
          </div>
        </li>
        <li v-if="!treeRows.length" class="empty">
          {{ busy ? '正在读取组织…' : error ? '目录读取失败' : '没有匹配部门' }}
        </li>
      </ul>
      <div class="directory-footnote"><AppIcon name="shield" />仅展示你的可见部门范围</div>
    </section>
    <div v-if="selected" class="department-content">
      <section class="card department-summary" aria-labelledby="current-department-title">
        <div class="department-breadcrumb" aria-label="当前部门路径">
          <template v-for="(ancestor, index) in breadcrumbs" :key="ancestor.id"
            ><span v-if="index" aria-hidden="true">/</span
            ><button :disabled="saving" @click="selectDepartment(ancestor)">
              {{ ancestor.name }}
            </button></template
          >
        </div>
        <div class="department-summary-heading">
          <div class="department-identity">
            <span class="department-emblem"><AppIcon :name="departmentIcon(selected)" /></span>
            <div>
              <div class="department-title">
                <h2 id="current-department-title">{{ selected.name }}</h2>
                <span class="badge blue">{{ labels[selected.type] }}</span>
              </div>
              <p>
                {{
                  selected.id === session.me.user.departmentId
                    ? '你的直属部门'
                    : '上级部门：' + (breadcrumbs.at(-2)?.name || '无')
                }}
              </p>
            </div>
          </div>
          <div class="department-actions">
            <button
              class="secondary icon-label"
              :disabled="saving || !canRoute('GET', '/users')"
              :title="!canRoute('GET', '/users') ? '没有查看员工的权限' : ''"
              @click="members = selected"
            >
              <AppIcon name="users" />查看员工</button
            ><button
              class="primary icon-label"
              :disabled="saving || !selected.canCreate"
              @click="create(selected)"
            >
              <AppIcon name="plus" />新增下级
            </button>
          </div>
        </div>
        <div class="department-stats">
          <div>
            <span>直属员工</span><strong>{{ selected.directUsers }}<small>人</small></strong>
          </div>
          <div>
            <span>总人数（含下级）</span><strong>{{ selected.totalUsers }}<small>人</small></strong>
          </div>
          <div>
            <span>直属下级部门</span><strong>{{ directChildren.length }}<small>个</small></strong>
          </div>
        </div>
        <div class="department-summary-footer">
          <span>人数包含停用账号；总人数已含下级，父子节点不重复相加。</span>
          <div>
            <button
              class="text-button"
              :disabled="saving || !selected.canEdit"
              @click="rename(selected)"
            >
              修改当前部门名称</button
            ><button
              class="text-button danger"
              :disabled="saving || !selected.canDelete"
              title="仅允许删除无人且无下级的可维护部门"
              @click="openDelete(selected)"
            >
              删除当前部门
            </button>
          </div>
        </div>
      </section>
      <section class="card department-children" aria-labelledby="department-children-title">
        <div class="directory-heading">
          <div>
            <h2 id="department-children-title">
              直属下级部门 <span class="directory-count">{{ directChildren.length }}</span>
            </h2>
            <p>点击部门名称查看下一级</p>
          </div>
          <span class="badge">仅当前部门的直接下级</span>
        </div>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>部门名称</th>
                <th>组织类型</th>
                <th>直属人数</th>
                <th>总人数（含下级）</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="node in directChildren" :key="node.id" :data-department-id="node.id">
                <td>
                  <button
                    class="department-child-name"
                    :disabled="saving"
                    @click="selectDepartment(node)"
                  >
                    <AppIcon :name="departmentIcon(node)" /><strong>{{ node.name }}</strong
                    ><AppIcon name="chevron" />
                  </button>
                </td>
                <td>
                  <span class="badge">{{ labels[node.type] }}</span>
                </td>
                <td>{{ node.directUsers }} 人</td>
                <td>
                  <strong>{{ node.totalUsers }}</strong> 人
                </td>
                <td class="row-actions">
                  <button
                    class="text-button"
                    :disabled="saving || !canRoute('GET', '/users')"
                    :title="!canRoute('GET', '/users') ? '没有查看员工的权限' : ''"
                    @click="members = node"
                  >
                    查看员工</button
                  ><button
                    class="text-button"
                    :disabled="saving || !node.canEdit"
                    @click="rename(node)"
                  >
                    修改名称</button
                  ><button
                    class="text-button danger"
                    :disabled="saving || !node.canDelete"
                    title="仅允许删除无人且无下级的可维护部门"
                    @click="openDelete(node)"
                  >
                    删除
                  </button>
                </td>
              </tr>
              <tr v-if="!directChildren.length">
                <td colspan="5" class="empty">
                  <strong>暂无直属下级部门</strong
                  ><small>{{
                    selected.canCreate
                      ? '可通过“新增下级”创建允许类型的部门。'
                      : selected.type === 'PROJECT'
                        ? '项目是末级节点，可查看员工或维护当前部门。'
                        : '当前没有可维护的下级部门。'
                  }}</small>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <div class="department-policy">
        <AppIcon name="info" />
        <p>
          下级类型由当前部门决定；项目不可新增下级。局部管理者只能改删严格下级；仅能删除无人且无下级的空部门，根部门不可删除。
        </p>
      </div>
    </div>
    <section v-else class="card empty">
      {{ busy ? '正在加载部门详情…' : error ? '请重试读取部门目录' : '暂无可见部门' }}
    </section>
  </div>
  <Modal
    v-if="editing"
    :title="editing.create ? '新增下级部门' : '修改部门名称'"
    :busy="saving"
    @close="editing = null"
  >
    <form @submit.prevent="save">
      <template v-if="editing.create">
        <label>
          上级部门
          <select v-model="parentId" required @change="type = childTypes[0] || ''">
            <option v-for="parent in parents" :key="parent.id" :value="String(parent.id)">
              {{ '　'.repeat(parent.depth) + parent.name }}
            </option>
          </select>
        </label>
        <label>
          组织类型
          <select v-model="type" required>
            <option v-for="value in childTypes" :key="value" :value="value">
              {{ labels[value] }}
            </option>
          </select>
        </label>
      </template>
      <label>
        部门名称
        <input
          v-model="name"
          required
          maxlength="100"
          placeholder="1至100字符，同一上级下不可重名"
        />
      </label>
      <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
      <p v-if="actionDenied" class="error">
        权限或范围已变化，本次操作已禁用；请关闭弹窗并刷新后重新进入。
      </p>
      <footer class="dialog-actions">
        <button type="button" class="secondary" :disabled="saving" @click="editing = null">
          取消
        </button>
        <button
          class="primary"
          :disabled="actionDenied || saving || !name.trim() || (editing.create && !type)"
        >
          保存
        </button>
      </footer>
    </form>
  </Modal>
  <Modal v-if="deleting" title="确认删除空部门" :busy="saving" @close="deleting = null">
    <p>确定删除“{{ deleting.name }}”？此操作不能恢复，不会删除人员或其他部门。</p>
    <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
    <p v-if="actionDenied" class="error">
      权限或范围已变化，本次操作已禁用；请关闭弹窗并刷新后重新进入。
    </p>
    <footer class="dialog-actions">
      <button class="secondary" :disabled="saving" @click="deleting = null">取消</button>
      <button class="primary" :disabled="actionDenied || saving" @click="remove(deleting)">
        确认删除
      </button>
    </footer>
  </Modal>
  <MemberList v-if="members" :key="members.id" :department="members" @close="members = null" />
</template>
<style scoped>
.department-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
}
.department-toolbar p {
  margin: 0;
  font-size: 12px;
}
.icon-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
}
:deep(.ui-icon) {
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
}
.department-layout {
  display: grid;
  grid-template-columns: 270px minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}
.department-directory {
  position: sticky;
  top: 22px;
}
.directory-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 22px 20px 16px;
  flex-wrap: wrap;
}
.directory-heading h2 {
  margin: 0;
  font-size: 15px;
  color: #2d476b;
}
.directory-heading p {
  color: #96a3b6;
  font-size: 11px;
  margin: 8px 0 0;
}
.directory-count {
  display: inline-block;
  border-radius: 4px;
  padding: 1px 6px;
  background: #f0f4fa;
  font-size: 10px;
  color: #91a2b9;
  font-weight: 400;
}
.directory-search {
  margin: 0 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border: 1px solid #dfe6f0;
  border-radius: 7px;
  color: #91a2b9;
}
.directory-search input {
  width: 100%;
  border: 0;
  padding: 10px 0;
  font-size: 12px;
  background: transparent;
}
.directory-search:focus-within {
  outline: 2px solid #93b8ff;
  outline-offset: 2px;
}
.directory-search input:focus-visible {
  outline: 0;
}
.directory-caption {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px 8px;
  color: #9aa7ba;
  font-size: 11px;
}
.directory-caption button {
  font-size: 11px;
  padding: 3px 0;
}
.department-tree {
  list-style: none;
  padding: 0 10px 20px;
  margin: 0;
  max-height: 590px;
  overflow: auto;
}
.department-tree-row {
  display: flex;
  align-items: center;
  border-radius: 7px;
  margin: 3px 0;
}
.department-tree-row:hover {
  background: #f6f9fd;
}
.department-tree-row.selected {
  color: #3865e8;
  background: #edf3ff;
}
.department-toggle,
.department-toggle-spacer {
  display: grid;
  place-items: center;
  width: 24px;
  height: 34px;
  padding: 0;
  flex: 0 0 24px;
  background: transparent;
}
.department-toggle :deep(.ui-icon) {
  width: 11px;
  height: 11px;
}
.department-toggle.expanded :deep(.ui-icon) {
  transform: rotate(90deg);
}
.department-select {
  display: flex;
  align-items: center;
  gap: 7px;
  text-align: left;
  min-width: 0;
  width: 100%;
  padding: 11px 8px 11px 0;
  background: transparent;
  font-size: 12px;
  color: inherit;
}
.department-select :deep(.ui-icon) {
  width: 15px;
  height: 15px;
  color: #8a9fbd;
}
.department-select span {
  flex: 1;
  overflow-wrap: anywhere;
}
.department-select small {
  font-size: 10px;
  color: #92a1b7;
}
.directory-footnote {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 15px 18px;
  font-size: 11px;
  color: #93a1b5;
  background: #fafcff;
  border-top: 1px solid #edf0f6;
}
.directory-footnote :deep(.ui-icon) {
  width: 14px;
  height: 14px;
}
.department-content {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.department-breadcrumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 20px 24px 0;
  font-size: 11px;
  color: #b0baca;
}
.department-breadcrumb button {
  padding: 0;
  background: transparent;
  color: #8a9ab0;
  font-size: 11px;
}
.department-breadcrumb button:last-child {
  color: #4b6b96;
}
.department-summary-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 20px;
  padding: 22px 24px 26px;
}
.department-identity {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.department-emblem {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  flex: 0 0 48px;
  border-radius: 12px;
  background: #edf3ff;
  color: #3865e8;
}
.department-emblem :deep(.ui-icon) {
  width: 25px;
  height: 25px;
}
.department-title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.department-title h2 {
  font-size: 20px;
  color: #263f64;
  margin: 0;
  overflow-wrap: anywhere;
}
.department-title .badge {
  margin: 0;
  font-size: 10px;
}
.department-identity p {
  margin: 8px 0 0;
  font-size: 11px;
  color: #91a0b5;
}
.department-actions {
  display: flex;
  gap: 9px;
  flex-wrap: wrap;
}
.department-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin: 0 24px;
  padding: 23px 0;
  border-top: 1px solid #edf0f6;
}
.department-stats > div {
  padding-left: 26px;
  border-left: 1px solid #edf0f6;
}
.department-stats > div:first-child {
  padding-left: 0;
  border-left: 0;
}
.department-stats span {
  display: block;
  font-size: 12px;
  color: #8a9ab0;
  margin-bottom: 12px;
}
.department-stats strong {
  font-size: 28px;
  line-height: 1;
  font-weight: 600;
  color: #314f77;
}
.department-stats small {
  display: inline;
  font-size: 11px;
  margin-left: 7px;
  font-weight: 400;
}
.department-summary-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  padding: 13px 24px;
  background: #fafcff;
  border-top: 1px solid #edf0f6;
}
.department-summary-footer > span {
  font-size: 10px;
  color: #99a6b9;
}
.department-summary-footer button {
  font-size: 11px;
}
.department-children .table-scroll {
  margin-top: 0;
}
.department-children table {
  min-width: 720px;
}
.department-children th {
  background: #f5f8fd;
  font-size: 11px;
}
.department-children td {
  vertical-align: middle;
  font-size: 12px;
  padding: 16px;
}
.department-children .row-actions {
  white-space: nowrap;
}
.department-children .text-button {
  padding: 5px;
  font-size: 12px;
}
.department-child-name {
  display: flex;
  align-items: center;
  gap: 8px;
  background: transparent;
  color: #4e6d95;
  padding: 6px 0;
  text-align: left;
}
.department-child-name strong {
  font-size: 12px;
  font-weight: 550;
}
.department-child-name :deep(.ui-icon) {
  width: 15px;
  height: 15px;
  color: #92a7c6;
}
.department-child-name :deep(.ui-icon:last-child) {
  width: 11px;
}
.department-policy {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  color: #98a5b8;
  font-size: 11px;
  padding: 0 4px;
}
.department-policy p {
  margin: 0;
  line-height: 1.9;
}
.department-policy :deep(.ui-icon) {
  width: 15px;
  height: 15px;
  margin-top: 3px;
}
@media (max-width: 1200px) {
  .department-layout {
    grid-template-columns: 240px minmax(0, 1fr);
    gap: 16px;
  }
}
@media (max-width: 820px) {
  .department-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .department-directory {
    position: static;
  }
  .department-tree {
    max-height: 245px;
  }
  .department-summary-heading,
  .department-summary-footer {
    padding-left: 18px;
    padding-right: 18px;
  }
  .department-breadcrumb {
    padding-left: 18px;
    padding-right: 18px;
  }
  .department-stats {
    margin: 0 18px;
  }
  .department-stats > div {
    padding-left: 12px;
  }
  .department-stats span {
    font-size: 10px;
  }
  .department-stats strong {
    font-size: 24px;
  }
}
</style>
