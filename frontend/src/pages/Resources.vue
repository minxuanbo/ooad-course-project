<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { api, canRoute, notify, refreshSession, session } from '../api.js'
import { errorText, organizationRows, RequestGeneration } from '../lib/presentation.js'
import Modal from '../components/Modal.vue'
import Pagination from '../components/Pagination.vue'
import CollectionEditor from '../components/CollectionEditor.vue'
import AppIcon from '../components/AppIcon.vue'
import PageActions from '../components/PageActions.vue'
const systems = ref([]),
  systemId = ref(''),
  nodes = ref([]),
  apis = ref([]),
  tab = ref('permissions'),
  q = ref(''),
  page = ref(0),
  total = ref(0),
  busy = ref(false),
  error = ref(''),
  editing = ref(null),
  mapping = ref(null),
  saving = ref(false),
  actionError = ref('')
const form = ref({ parentId: '', type: 'BUTTON', enabled: true })
const confirmChange = ref(false)
const actionDenied = ref(false)
const generation = new RequestGeneration()
const mappingGeneration = new RequestGeneration()
const selectedId = ref(null),
  permissionApis = ref(null),
  mappingBusy = ref(false),
  mappingError = ref('')
const selected = computed(() => nodes.value.find((node) => node.id === selectedId.value) || null)
const selectedSystem = computed(() =>
  systems.value.find((system) => String(system.id) === systemId.value),
)
const typeLabels = { MENU: '菜单', BUTTON: '按钮', API: 'API权限' }
const visible = computed(() =>
  organizationRows(nodes.value, q.value, (item, query) =>
    `${item.name} ${item.code}`.toLowerCase().includes(query),
  ),
)
/** 身份及可见根变化后，旧目录或映射不得继续填入当前工作区。 */
function identityKey() {
  return JSON.stringify([
    session.token,
    session.me?.user.id,
    session.me?.user.departmentId,
    session.me?.user.globalScope,
  ])
}
function clearMapping() {
  mappingGeneration.cancel()
  permissionApis.value = null
  mappingError.value = ''
  mappingBusy.value = false
}

/** 右侧始终读取选中节点的完整已保存映射，不用候选分页拼接代替。 */
async function loadPermissionApis() {
  clearMapping()
  const node = selected.value
  if (!node || !canRoute('GET', '/permissions/{id}/apis', true)) return
  const version = mappingGeneration.next(),
    identity = identityKey(),
    system = systemId.value
  mappingBusy.value = true
  try {
    const value = await api(`/permissions/${node.id}/apis`)
    if (
      mappingGeneration.current(version) &&
      identity === identityKey() &&
      system === systemId.value &&
      selectedId.value === node.id
    )
      permissionApis.value = value.items
  } catch (e) {
    if (mappingGeneration.current(version) && identity === identityKey())
      mappingError.value = errorText(e)
  } finally {
    if (mappingGeneration.current(version)) mappingBusy.value = false
  }
}
async function selectPermission(node) {
  if (busy.value || saving.value || !nodes.value.some((item) => item.id === node?.id)) return
  selectedId.value = node.id
  await loadPermissionApis()
}

/** 只查询服务端已接入目录，不创建原型中的演示业务系统。 */
async function load(next = 0) {
  const version = generation.next()
  const identity = identityKey()
  const requestedTab = tab.value
  busy.value = true
  error.value = ''
  nodes.value = []
  apis.value = []
  clearMapping()
  try {
    const registered = await api('/systems')
    if (!generation.current(version) || identity !== identityKey()) return
    systems.value = registered
    if (!systems.value.some((item) => String(item.id) === systemId.value))
      systemId.value = String(systems.value[0]?.id || '')
    if (!systemId.value) throw Error('尚未初始化RBAC资源目录。')
    if (requestedTab === 'permissions') {
      if (!canRoute('GET', '/permissions/tree', true)) throw Error('当前没有读取权限目录的权限。')
      const roots = await api(
        '/permissions/tree?' + new URLSearchParams({ systemId: systemId.value }),
      )
      if (!generation.current(version) || identity !== identityKey()) return
      const flatten = (items) =>
        items.forEach((item) => {
          nodes.value.push(item)
          flatten(item.children || [])
        })
      flatten(roots)
      if (!nodes.value.some((node) => node.id === selectedId.value))
        selectedId.value = nodes.value[0]?.id ?? null
      busy.value = false
      await loadPermissionApis()
    } else {
      if (!canRoute('GET', '/api-resources', true)) throw Error('当前没有查询API资源的权限。')
      const result = await api(
        '/api-resources?' +
          new URLSearchParams({
            systemId: systemId.value,
            q: q.value.trim(),
            page: next,
            size: 20,
          }),
      )
      if (!generation.current(version) || identity !== identityKey()) return
      apis.value = result.items
      page.value = next
      total.value = result.total
    }
  } catch (e) {
    if (generation.current(version) && identity === identityKey()) error.value = errorText(e)
  } finally {
    if (generation.current(version)) busy.value = false
  }
}
function edit(node, api = false) {
  editing.value = { ...node, api }
  form.value = {
    parentId: node.parentId == null ? '' : String(node.parentId),
    type: node.type || 'BUTTON',
    enabled: node.enabled,
  }
  actionError.value = ''
  actionDenied.value = false
  confirmChange.value = false
}
/** 显式null表示根权限；父级和类型规则仍由后端事务独立检查。 */
async function save() {
  if (actionDenied.value || saving.value) return
  if (!confirmChange.value) {
    confirmChange.value = true
    return
  }
  saving.value = true
  actionError.value = ''
  try {
    await api(
      editing.value.api ? `/api-resources/${editing.value.id}` : `/permissions/${editing.value.id}`,
      'PATCH',
      editing.value.api
        ? { enabled: form.value.enabled }
        : {
            parentId: form.value.parentId === '' ? null : Number(form.value.parentId),
            type: form.value.type,
            enabled: form.value.enabled,
          },
    )
    editing.value = null
    notify('资源属性已保存。')
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
  mapping.value = null
  await refreshSession()
  load()
}
async function selectTab(value) {
  tab.value = value
  q.value = ''
  await load(0)
}
async function changeSystem() {
  q.value = ''
  selectedId.value = null
  await load(0)
}
watch(identityKey, () => {
  generation.cancel()
  nodes.value = []
  apis.value = []
  selectedId.value = null
  mapping.value = null
  clearMapping()
})
onMounted(() => {
  if (!canRoute('GET', '/permissions/tree', true) && canRoute('GET', '/api-resources', true))
    tab.value = 'apis'
  load()
})
onUnmounted(() => {
  generation.cancel()
  mappingGeneration.cancel()
})
</script>
<template>
  <PageActions :busy="busy || saving" @refresh="load(page)" />
  <section class="resource-controls">
    <form class="resource-system" @submit.prevent="load(0)">
      <label for="resource-system">所属系统</label>
      <select
        id="resource-system"
        v-model="systemId"
        :disabled="busy || saving"
        @change="changeSystem"
      >
        <option v-for="system in systems" :key="system.id" :value="String(system.id)">
          {{ system.name }}
        </option>
      </select>
    </form>
    <div class="resource-tabs" aria-label="资源分类">
      <button
        v-if="canRoute('GET', '/permissions/tree', true)"
        :class="{ active: tab === 'permissions' }"
        :aria-pressed="tab === 'permissions'"
        :disabled="saving"
        @click="selectTab('permissions')"
      >
        权限目录
      </button>
      <button
        v-if="canRoute('GET', '/api-resources', true)"
        :class="{ active: tab === 'apis' }"
        :aria-pressed="tab === 'apis'"
        :disabled="saving"
        @click="selectTab('apis')"
      >
        API资源
      </button>
    </div>
  </section>
  <p v-if="error" class="error card panel" role="alert">
    {{ error }}
    <button class="secondary" @click="load(page)">重试</button>
  </p>
  <div v-if="tab === 'permissions'" class="resource-layout">
    <section class="card permission-directory">
      <div class="resource-section-heading">
        <h2>权限目录</h2>
        <span class="permission-count">{{ nodes.length }}</span>
      </div>
      <div class="permission-search">
        <AppIcon name="search" /><input
          v-model="q"
          aria-label="搜索权限名称或编码"
          placeholder="搜索名称或编码"
          maxlength="100"
        />
      </div>
      <div class="permission-tree" role="group" aria-label="权限目录节点">
        <button
          v-for="node in visible"
          :key="node.id"
          :class="{ selected: selectedId === node.id }"
          :style="{ paddingLeft: 16 + node.depth * 20 + 'px' }"
          :aria-current="selectedId === node.id ? 'true' : undefined"
          :disabled="saving || busy"
          @click="selectPermission(node)"
        >
          <AppIcon
            :name="node.type === 'MENU' ? 'folder' : node.type === 'API' ? 'resource' : 'button'"
          /><span>{{ node.name }}</span
          ><small v-if="!node.enabled">停用</small>
        </button>
        <p v-if="!visible.length" class="empty">
          {{ busy ? '正在读取权限…' : error ? '权限读取失败' : '没有匹配权限' }}
        </p>
      </div>
    </section>
    <section class="card permission-detail">
      <template v-if="selected"
        ><div class="resource-section-heading">
          <h2>{{ selected.name }}</h2>
          <span class="badge blue">{{ typeLabels[selected.type] }}</span>
        </div>
        <div class="permission-detail-body">
          <dl class="permission-fields">
            <dt>权限编码</dt>
            <dd class="mono">{{ selected.code }}</dd>
            <dt>所属系统</dt>
            <dd>{{ selectedSystem?.name }}</dd>
            <dt>上级权限</dt>
            <dd>{{ nodes.find((node) => node.id === selected.parentId)?.name || '无上级' }}</dd>
            <dt>启用状态</dt>
            <dd>
              <span class="resource-status" :class="{ off: !selected.enabled }">{{
                selected.enabled ? '启用' : '已停用'
              }}</span>
            </dd>
          </dl>
          <button
            v-if="canRoute('PATCH', '/permissions/{id}', true)"
            class="secondary"
            :disabled="saving"
            @click="edit(selected)"
          >
            编辑属性
          </button>
          <div class="permission-mapping-heading">
            <h3>
              已映射API <span>{{ permissionApis?.length ?? '—' }}</span>
            </h3>
            <button
              v-if="canRoute('GET', '/permissions/{id}/apis', true)"
              class="text-button"
              :disabled="busy || saving"
              @click="mapping = selected"
            >
              {{ canRoute('PUT', '/permissions/{id}/apis', true) ? '配置映射' : '查看映射' }}
            </button>
          </div>
          <p v-if="!canRoute('GET', '/permissions/{id}/apis', true)" class="muted">
            当前没有读取映射的权限。
          </p>
          <p v-else-if="mappingBusy" class="muted" role="status">正在读取完整API映射…</p>
          <p v-else-if="mappingError" class="error" role="alert">
            {{ mappingError
            }}<button class="text-button" @click="loadPermissionApis">重试映射</button>
          </p>
          <template v-else
            ><div v-for="item in permissionApis || []" :key="item.id" class="mapped-api">
              <span class="http-method" :class="item.method.toLowerCase()">{{ item.method }}</span
              ><code>{{ item.path }}</code
              ><span class="resource-status" :class="{ off: !item.enabled }">{{
                item.enabled ? '启用' : '停用'
              }}</span>
            </div>
            <p v-if="permissionApis && !permissionApis.length" class="mapping-empty">
              尚未关联API
            </p></template
          >
          <p class="resource-hint">
            {{
              selected.type === 'API'
                ? 'API权限必须完整映射同系统的启用接口，才导出有效权限短码。'
                : '每个操作独立授权；选择父菜单不会自动授予其子权限。'
            }}
          </p>
          <p class="resource-hint">停用父菜单不会级联停用直接授予的子权限。</p>
        </div></template
      >
      <p v-else class="empty">{{ busy ? '正在读取权限详情…' : '请选择一个权限节点' }}</p>
    </section>
  </div>
  <section v-else class="card api-directory">
    <form class="toolbar" @submit.prevent="load(0)">
      <label>搜索API资源<input v-model="q" maxlength="100" placeholder="HTTP方法或路径" /></label
      ><button class="primary" :disabled="busy">查询</button>
    </form>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>请求方法</th>
            <th>服务端路径模板</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in apis" :key="item.id">
            <td>
              <span class="badge blue">{{ item.method }}</span>
            </td>
            <td class="mono">{{ item.path }}</td>
            <td>
              <span class="badge" :class="item.enabled ? 'green' : 'red'">
                {{ item.enabled ? '启用' : '停用' }}
              </span>
            </td>
            <td>
              <button
                v-if="canRoute('PATCH', '/api-resources/{id}', true)"
                class="text-button"
                @click="edit(item, true)"
              >
                启停设置
              </button>
            </td>
          </tr>
          <tr v-if="!apis.length">
            <td colspan="4" class="empty">
              {{ busy ? '正在读取API…' : error ? 'API读取失败' : '暂无匹配API资源' }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <Pagination :page="page" :size="20" :total="total" :busy="busy" @change="load" />
  </section>
  <Modal
    v-if="editing"
    :title="editing.api ? 'API启停' : '权限属性'"
    :busy="saving"
    @close="editing = null"
  >
    <form @submit.prevent="save" @input="confirmChange = false" @change="confirmChange = false">
      <p>{{ editing.name || editing.method + ' ' + editing.path }}</p>
      <template v-if="!editing.api">
        <label>
          父权限
          <select v-model="form.parentId">
            <option value="">无父级</option>
            <option
              v-for="item in nodes.filter((item) => item.id !== editing.id && item.type === 'MENU')"
              :key="item.id"
              :value="String(item.id)"
            >
              {{ item.name }} · {{ item.code }}
            </option>
          </select>
        </label>
        <label>
          类型
          <select v-model="form.type">
            <option value="MENU">菜单</option>
            <option value="BUTTON">按钮</option>
            <option value="API">API权限（必须映射接口）</option>
          </select>
        </label>
      </template>
      <label class="checkbox-label">
        <input v-model="form.enabled" type="checkbox" />
        启用资源
      </label>
      <p class="muted">
        停用、撤映射或撤权提交后，新请求立即按最新状态判定。完整维护者保护可能拒绝危险操作。
      </p>
      <p v-if="actionError" class="error" role="alert">{{ actionError }}</p>
      <p v-if="actionDenied" class="error">
        权限或范围已变化，本次操作已禁用；请关闭弹窗并刷新后重新进入。
      </p>
      <p v-if="confirmChange && !actionDenied" class="pending-query" role="alert">
        权限资源为共享配置，此次修改将影响所有引用该权限或API的角色和用户。请核对后确认保存。
      </p>
      <footer class="dialog-actions">
        <button type="button" class="secondary" :disabled="saving" @click="editing = null">
          取消
        </button>
        <button class="primary" :disabled="actionDenied || saving">
          {{ confirmChange ? '确认保存共享配置' : '保存' }}
        </button>
      </footer>
    </form>
  </Modal>
  <CollectionEditor
    v-if="mapping"
    :key="mapping.id"
    :title="mapping.name + ' · API映射'"
    kind="apis"
    :system-id="mapping.systemId"
    :read-path="`/permissions/${mapping.id}/apis`"
    :save-path="`/permissions/${mapping.id}/apis`"
    :readonly="!canRoute('PUT', '/permissions/{id}/apis', true)"
    @close="mapping = null"
    @saved="saved"
  />
</template>
<style scoped>
.resource-toolbar p {
  font-size: 12px;
  margin-bottom: 23px;
}
.resource-system {
  display: flex;
  align-items: center;
  gap: 14px;
}
.resource-system label {
  font-size: 12px;
  white-space: nowrap;
  color: #577096;
}
.resource-system select {
  flex: 1;
  min-width: 0;
  background: white;
}
.resource-tabs {
  display: flex;
  align-items: center;
  gap: 26px;
  margin: 18px 0;
  border-bottom: 1px solid #e9edf5;
}
.resource-tabs button {
  background: transparent;
  border-radius: 0;
  padding: 12px 2px;
  font-size: 12px;
  color: #91a2bd;
}
.resource-tabs button.active {
  color: #3865ef;
  font-weight: 600;
  box-shadow: inset 0 -2px #3865ef;
}
.resource-layout {
  display: grid;
  grid-template-columns: 270px minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}
.resource-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 22px 20px;
}
.resource-section-heading h2 {
  margin: 0;
  font-size: 15px;
  color: #365071;
  overflow-wrap: anywhere;
}
.resource-section-heading .badge {
  margin: 0;
  font-size: 11px;
}
.permission-count {
  color: #9baac0;
  font-size: 10px;
  padding: 1px 6px;
  background: #f2f5fa;
  border-radius: 4px;
}
.permission-search {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #e1e7f1;
  border-radius: 6px;
  margin: 0 14px 12px;
  padding: 0 10px;
  color: #99a9bf;
}
.permission-search input {
  border: 0;
  font-size: 12px;
  width: 100%;
  padding: 9px 0;
}
.permission-search:focus-within {
  outline: 2px solid #93b8ff;
  outline-offset: 2px;
}
.permission-search input:focus-visible {
  outline: 0;
}
.permission-tree {
  max-height: 570px;
  overflow-y: auto;
  padding: 0 8px 16px;
}
.permission-tree button {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 8px;
  padding: 12px 10px;
  text-align: left;
  color: #7890b2;
  font-size: 12px;
  background: transparent;
  min-height: 40px;
}
.permission-tree button:hover {
  background: #f7f9fd;
}
.permission-tree button.selected {
  background: #edf3ff;
  color: #3865ef;
}
.permission-tree button :deep(.ui-icon) {
  width: 14px;
  height: 14px;
  color: #a3b9db;
}
.permission-tree button span {
  flex: 1;
  overflow-wrap: anywhere;
}
.permission-tree button small {
  font-size: 10px;
}
.permission-detail {
  min-width: 0;
}
.permission-detail-body {
  padding: 10px 24px 22px;
}
.permission-fields {
  display: grid;
  grid-template-columns: 100px minmax(0, 1fr);
  gap: 24px 14px;
  margin: 0 0 28px;
  font-size: 12px;
}
.permission-fields dt {
  color: #95a6c1;
}
.permission-fields dd {
  margin: 0;
  color: #415b7c;
  overflow-wrap: anywhere;
}
.permission-fields .mono {
  font-size: 11px;
}
.permission-detail-body > .secondary {
  font-size: 12px;
}
.resource-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #24977d;
  font-size: 11px;
  white-space: nowrap;
}
.resource-status::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}
.resource-status.off {
  color: #9aa7b9;
}
.permission-mapping-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 24px;
  border-top: 1px solid #edf0f6;
  margin-top: 24px;
  margin-bottom: 12px;
}
.permission-mapping-heading h3 {
  font-size: 12px;
  font-weight: 600;
  color: #5b769a;
  margin: 0;
}
.permission-mapping-heading h3 span {
  margin-left: 4px;
  color: #95a6bf;
  font-weight: 400;
}
.permission-mapping-heading .text-button {
  font-size: 12px;
}
.mapped-api {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 16px 0;
  border-bottom: 1px solid #f0f3f8;
}
.mapped-api code {
  flex: 1;
  min-width: 100px;
  overflow-wrap: anywhere;
  font-size: 11px;
  color: #3c5578;
}
.http-method {
  background: #eef7f3;
  color: #4b9c83;
  border-radius: 3px;
  font-size: 9px;
  padding: 2px 5px;
  font-family: ui-monospace, monospace;
}
.http-method.post {
  background: #edf3ff;
  color: #3865e8;
}
.http-method.put,
.http-method.patch {
  background: #fff5e8;
  color: #bb8a40;
}
.http-method.delete {
  background: #fff0f0;
  color: #c96c6c;
}
.resource-hint,
.mapping-empty {
  color: #91a3c0;
  font-size: 11px;
  margin: 16px 0 0;
  line-height: 1.9;
}
.api-directory .toolbar {
  padding: 18px 22px;
  margin: 0;
}
.api-directory .table-scroll {
  margin-top: 0;
}
@media (max-width: 1100px) {
  .resource-layout {
    grid-template-columns: 240px minmax(0, 1fr);
    gap: 16px;
  }
}
@media (max-width: 820px) {
  .resource-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .permission-tree {
    max-height: 260px;
  }
  .permission-detail-body {
    padding: 10px 18px 20px;
  }
  .permission-fields {
    grid-template-columns: 82px minmax(0, 1fr);
    gap: 20px 10px;
  }
}
</style>
