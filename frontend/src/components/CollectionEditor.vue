<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import Modal from './Modal.vue'
import Pagination from './Pagination.vue'
import { api, notify } from '../api.js'
import { errorText, RequestGeneration } from '../lib/presentation.js'
const props = defineProps({
  title: String,
  readPath: String,
  savePath: String,
  kind: String,
  readonly: Boolean,
})
const emit = defineEmits(['close', 'saved'])
const selected = ref(new Set()),
  labels = ref(new Map()),
  ready = ref(false),
  loading = ref(false),
  saving = ref(false),
  error = ref('')
const systems = ref([]),
  systemId = ref(''),
  q = ref(''),
  candidates = ref([]),
  candidateError = ref(''),
  candidateBusy = ref(false),
  page = ref(0),
  total = ref(0)
const confirmEmpty = ref(false)
const confirmSave = ref(false),
  accessDenied = ref(false)
const initialGeneration = new RequestGeneration(),
  candidateGeneration = new RequestGeneration()
const visible = computed(() =>
  candidates.value.filter(
    (item) =>
      props.kind !== 'permissions' ||
      `${item.name} ${item.code}`.toLowerCase().includes(q.value.trim().toLowerCase()),
  ),
)
/** 完整已保存集合读取成功前，不展示可覆盖保存的空白编辑器。 */
async function loadInitial() {
  const version = initialGeneration.next()
  loading.value = true
  error.value = ''
  ready.value = false
  try {
    const value = await api(props.readPath)
    if (!initialGeneration.current(version)) return
    selected.value = new Set(value.ids || value.roles?.map((item) => item.id) || [])
    for (const item of value.items || value.roles || [])
      labels.value.set(item.id, item.name || `${item.method} ${item.path}`)
    ready.value = true
    if (!props.readonly) {
      await loadCandidates(0)
    }
  } catch (e) {
    if (initialGeneration.current(version)) {
      if (ready.value) candidateError.value = errorText(e)
      else error.value = errorText(e)
    }
  } finally {
    if (initialGeneration.current(version)) loading.value = false
  }
}
/** 候选分页与已选集合独立，切换系统、搜索和重试均不清空已保存或已选ID。 */
async function loadCandidates(next = 0) {
  if (!ready.value || props.readonly || accessDenied.value) return
  const version = candidateGeneration.next()
  candidateBusy.value = true
  candidateError.value = ''
  page.value = next
  try {
    if (props.kind !== 'roles' && !systems.value.length) {
      const registered = await api('/systems')
      if (!candidateGeneration.current(version)) return
      systems.value = registered
      systemId.value = String(registered[0]?.id || '')
      if (!systemId.value) throw Error('尚未初始化RBAC资源目录。')
    }
    let items, count
    if (props.kind === 'roles') {
      const result = await api(
        props.savePath.replace(/\/roles$/, '/assignable-roles') +
          '?' +
          new URLSearchParams({ page: next, size: 30, q: q.value.trim() }),
      )
      items = result.items
      count = result.total
    } else if (props.kind === 'apis') {
      const result = await api(
        '/api-resources?' +
          new URLSearchParams({
            systemId: systemId.value,
            page: next,
            size: 30,
            q: q.value.trim(),
          }),
      )
      items = result.items
      count = result.total
    } else {
      const roots = await api(
        '/permissions/tree?' + new URLSearchParams({ systemId: systemId.value }),
      )
      items = []
      const flatten = (nodes, depth = 0) =>
        nodes.forEach((item) => {
          items.push({ ...item, depth })
          flatten(item.children || [], depth + 1)
        })
      flatten(roots)
      count = items.length
    }
    if (!candidateGeneration.current(version)) return
    candidates.value = items
    total.value = count
    for (const item of items) labels.value.set(item.id, item.name || `${item.method} ${item.path}`)
  } catch (e) {
    if (candidateGeneration.current(version)) {
      candidates.value = []
      candidateError.value = errorText(e)
    }
  } finally {
    if (candidateGeneration.current(version)) candidateBusy.value = false
  }
}
function toggle(id, checked) {
  confirmEmpty.value = false
  confirmSave.value = false
  const value = new Set(selected.value)
  checked ? value.add(id) : value.delete(id)
  selected.value = value
}
/** 整组提交所有页与系统的已选ID，空选需确认，不从当前候选推导提交集合。 */
async function save() {
  if (!ready.value || saving.value || props.readonly || accessDenied.value) return
  if (!confirmSave.value) {
    confirmSave.value = true
    confirmEmpty.value = !selected.value.size
    return
  }
  saving.value = true
  error.value = ''
  try {
    await api(props.savePath, 'PUT', { ids: [...selected.value] })
    notify('已保存，后续请求按最新授权生效。')
    emit('saved')
  } catch (e) {
    error.value = errorText(e)
    if (e.status === 403) {
      accessDenied.value = true
      emit('invalidated')
    }
  } finally {
    saving.value = false
  }
}
onMounted(loadInitial)
onUnmounted(() => {
  initialGeneration.cancel()
  candidateGeneration.cancel()
})
</script>
<template>
  <Modal :title="title" :busy="saving" @close="emit('close')">
    <p class="muted">账号、角色与资源状态独立。停用关联仍保留，且不贡献有效权限。</p>
    <p v-if="loading && !ready" role="status">正在读取完整已保存集合…</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <button v-if="!ready && !loading" class="secondary" @click="loadInitial">完整重试</button>
    <template v-if="ready">
      <div class="selected-summary">
        <strong>已选 {{ selected.size }} 项</strong>
        <p v-if="!selected.size" class="muted">当前未授予任何项</p>
        <div class="selected-chips">
          <span v-for="id in selected" :key="id" class="selection-chip">
            {{ labels.get(id) || `ID ${id}（不在当前候选）` }}
            <button
              v-if="!readonly"
              :disabled="saving"
              :aria-label="'移除ID ' + id"
              @click="toggle(id, false)"
            >
              ×
            </button>
          </span>
        </div>
      </div>
      <template v-if="!readonly">
        <form class="toolbar chooser-filters" @submit.prevent="loadCandidates(0)">
          <label v-if="kind !== 'roles'">
            所属系统
            <select v-model="systemId" :disabled="saving || loading" @change="loadCandidates(0)">
              <option v-for="system in systems" :key="system.id" :value="String(system.id)">
                {{ system.name }}
              </option>
            </select>
          </label>
          <label>
            搜索候选
            <input v-model="q" maxlength="100" placeholder="名称或编码" />
          </label>
          <button class="secondary" :disabled="saving || loading || candidateBusy">查询</button>
        </form>
        <p v-if="candidateError" class="error" role="alert">
          {{ candidateError }}
          <button class="secondary" @click="loadCandidates(page)">重试候选</button>
        </p>
        <p v-if="candidateBusy" role="status">正在读取候选…</p>
        <div v-else class="candidate-list">
          <label
            v-for="item in visible"
            :key="item.id"
            :style="{ paddingLeft: 12 + (item.depth || 0) * 18 + 'px' }"
          >
            <input
              type="checkbox"
              :checked="selected.has(item.id)"
              :disabled="saving || (!item.enabled && !selected.has(item.id))"
              @change="toggle(item.id, $event.target.checked)"
            />
            <span>
              {{ item.name || `${item.method} ${item.path}` }}
              <small>{{ item.code || item.id }} · {{ item.enabled ? '启用' : '停用' }}</small>
            </span>
          </label>
          <p v-if="!visible.length && !candidateError" class="empty">没有符合条件的候选</p>
        </div>
        <Pagination
          v-if="kind !== 'permissions'"
          :page="page"
          :size="30"
          :total="total"
          :busy="candidateBusy || saving"
          @change="loadCandidates"
        />
        <p class="muted">跨页已选项会完整保留；角色授予还受级别、人员范围和有效权限子集限制。</p>
      </template>
    </template>
    <p v-if="accessDenied" class="error" role="alert">
      权限或人员范围已变化，已禁用本次保存。请关闭弹窗并刷新列表后重新进入；草稿未提交。
    </p>
    <p v-if="confirmSave && !accessDenied" class="pending-query" role="alert">
      {{ title }}：{{
        kind === 'permissions'
          ? '此为共享角色配置，将影响该角色全部持有者。'
          : kind === 'apis'
            ? '此为共享权限资源，将影响所有引用它的角色和用户。'
            : '将整体替换该人员当前全部角色关联。'
      }}{{
        confirmEmpty ? '将撤销全部关联。' : `将保存完整的 ${selected.size} 项关联。`
      }}请再次确认，提交后立即生效。
    </p>
    <footer class="dialog-actions">
      <button class="secondary" :disabled="saving" @click="emit('close')">
        {{ readonly ? '关闭' : '取消' }}
      </button>
      <button
        v-if="!readonly"
        class="primary"
        :disabled="!ready || loading || saving || accessDenied"
        @click="save"
      >
        {{
          saving
            ? '保存中…'
            : confirmEmpty
              ? '确认撤销全部关联'
              : confirmSave
                ? '确认保存完整集合'
                : '保存完整集合'
        }}
      </button>
    </footer>
  </Modal>
</template>
