<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { api, canRoute, notify, refreshSession } from '../api.js'
import { errorText, organizationRows, RequestGeneration } from '../lib/presentation.js'
import Modal from '../components/Modal.vue'
import Pagination from '../components/Pagination.vue'
import CollectionEditor from '../components/CollectionEditor.vue'
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
const visible = computed(() =>
  organizationRows(nodes.value, q.value, (item, query) =>
    `${item.name} ${item.code}`.toLowerCase().includes(query),
  ),
)

/** 只查询已登记的RBAC目录，不创建原型中的演示业务系统。 */
async function load(next = 0) {
  const version = generation.next()
  busy.value = true
  error.value = ''
  nodes.value = []
  apis.value = []
  try {
    systems.value = await api('/systems')
    if (!generation.current(version)) return
    if (!systems.value.some((item) => String(item.id) === systemId.value))
      systemId.value = String(systems.value[0]?.id || '')
    if (!systemId.value) throw Error('尚未初始化RBAC资源目录。')
    if (tab.value === 'permissions') {
      const roots = await api(
        '/permissions/tree?' + new URLSearchParams({ systemId: systemId.value }),
      )
      if (!generation.current(version)) return
      const flatten = (items) =>
        items.forEach((item) => {
          nodes.value.push(item)
          flatten(item.children || [])
        })
      flatten(roots)
    } else {
      const result = await api(
        '/api-resources?' +
          new URLSearchParams({
            systemId: systemId.value,
            q: q.value.trim(),
            page: next,
            size: 20,
          }),
      )
      if (!generation.current(version)) return
      apis.value = result.items
      page.value = next
      total.value = result.total
    }
  } catch (e) {
    if (generation.current(version)) error.value = errorText(e)
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
function selectTab(value) {
  tab.value = value
  q.value = ''
  load(0)
}
onMounted(() => load())
onUnmounted(() => generation.cancel())
</script>
<template>
  <section class="card panel">
    <h2>权限资源与 API 映射</h2>
    <p class="muted">
      当前管理RBAC自身的菜单、按钮及后端接口。菜单父子关系用于组织展示，不自动继承权限。
    </p>
    <form class="toolbar" @submit.prevent="load(0)">
      <label>
        所属系统
        <select v-model="systemId" @change="load(0)">
          <option v-for="system in systems" :key="system.id" :value="String(system.id)">
            {{ system.name }}
          </option>
        </select>
      </label>
      <label>
        搜索资源
        <input v-model="q" maxlength="100" placeholder="名称、编码或API路径" />
      </label>
      <button class="secondary" :disabled="busy">查询</button>
    </form>
    <div class="tabs">
      <button :class="{ active: tab === 'permissions' }" @click="selectTab('permissions')">
        菜单与按钮
      </button>
      <button :class="{ active: tab === 'apis' }" @click="selectTab('apis')">API资源</button>
    </div>
  </section>
  <p v-if="error" class="error card panel" role="alert">
    {{ error }}
    <button class="secondary" @click="load(page)">重试</button>
  </p>
  <section v-if="tab === 'permissions'" class="card table-scroll">
    <table>
      <thead>
        <tr>
          <th>权限名称 / 编码</th>
          <th>类型</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="node in visible" :key="node.id">
          <td :style="{ paddingLeft: 20 + node.depth * 22 + 'px' }">
            {{ node.name }}
            <small>{{ node.code }}</small>
          </td>
          <td>{{ node.type === 'MENU' ? '菜单' : '按钮' }}</td>
          <td>
            <span class="badge" :class="node.enabled ? 'green' : 'red'">
              {{ node.enabled ? '启用' : '停用' }}
            </span>
          </td>
          <td>
            <button
              v-if="canRoute('PATCH', '/permissions/{id}', true)"
              class="text-button"
              @click="edit(node)"
            >
              编辑属性
            </button>
            <button
              v-if="canRoute('GET', '/permissions/{id}/apis', true)"
              class="text-button"
              @click="mapping = node"
            >
              API映射
            </button>
          </td>
        </tr>
        <tr v-if="!visible.length">
          <td colspan="4" class="empty">
            {{ busy ? '正在读取权限…' : error ? '权限读取失败' : '没有匹配权限' }}
          </td>
        </tr>
      </tbody>
    </table>
  </section>
  <section v-else class="card table-scroll">
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
    :read-path="`/permissions/${mapping.id}/apis`"
    :save-path="`/permissions/${mapping.id}/apis`"
    :readonly="!canRoute('PUT', '/permissions/{id}/apis', true)"
    @close="mapping = null"
    @saved="saved"
  />
</template>
