<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { api, session } from '../api.js'
import { RequestGeneration, errorText } from '../lib/presentation.js'
import Modal from './Modal.vue'
import Pagination from './Pagination.vue'
const props = defineProps({ department: Object, role: Object })
defineEmits(['close'])
const rows = ref([]), total = ref(0), page = ref(0), busy = ref(false), error = ref('')
const q = ref(''), enabled = ref(''), scope = ref('DIRECT'), applied = ref('')
const generation = new RequestGeneration()
/** 列表只读，身份变化后不回填之前请求返回的人员。 */
function identityKey() {
  return JSON.stringify([session.token, session.me?.user?.departmentId])
}
/** 分页使用已应用条件，草稿改变后由用户重新查询。 */
function queryKey() {
  return JSON.stringify([q.value.trim(), enabled.value, scope.value])
}
/** 相同组件用于两种入口，数据与总数均由受限人员接口提供。 */
async function load(next = 0) {
  const version = generation.next(), identity = identityKey(), filter = queryKey()
  busy.value = true
  rows.value = []
  total.value = 0
  error.value = ''
  try {
    const query = new URLSearchParams({ page: next, size: 20, q: q.value.trim() })
    if (enabled.value) query.set('enabled', enabled.value)
    if (props.department) {
      query.set('departmentId', props.department.id)
      query.set('departmentScope', scope.value)
    }
    if (props.role) query.set('roleId', props.role.id)
    const result = await api('/users?' + query)
    if (generation.current(version) && identity === identityKey() && filter === queryKey()) {
      rows.value = result.items
      total.value = result.total
      page.value = next
      applied.value = filter
    }
  } catch (e) {
    if (generation.current(version) && identity === identityKey() && filter === queryKey())
      error.value = errorText(e)
  } finally {
    if (generation.current(version)) busy.value = false
  }
}
onMounted(() => load())
onUnmounted(() => generation.cancel())
</script>
<template>
  <Modal :title="(department?.name || role?.name || '') + ' · 员工名单'" @close="$emit('close')">
    <p class="muted">只读名单；仅展示当前可见部门范围内的员工。</p>
    <p v-if="role && !role.enabled" class="pending-query">
      此角色已停用；名单保留已分配关系，角色当前不贡献权限。
    </p>
    <form class="toolbar" @submit.prevent="load(0)">
      <label>搜索员工<input v-model="q" maxlength="100" placeholder="姓名或工号" /></label>
      <label v-if="department">查看范围
        <select v-model="scope" @change="load(0)">
          <option value="DIRECT">仅直属员工</option>
          <option value="SUBTREE">包含下级部门</option>
        </select>
      </label>
      <label>账号状态
        <select v-model="enabled">
          <option value="">全部</option><option value="true">启用</option><option value="false">停用</option>
        </select>
      </label>
      <button class="secondary" :disabled="busy">查询</button>
    </form>
    <p v-if="error" class="error" role="alert">
      {{ error }}<button class="secondary" :disabled="busy" @click="load(0)">重试</button>
    </p>
    <p v-else-if="!busy && applied && applied !== queryKey()" class="pending-query">筛选已修改，请点击查询。</p>
    <p>当前范围匹配 {{ total }} 人</p>
    <div class="table-scroll">
      <table>
        <thead><tr><th>姓名 / 工号</th><th>所属部门</th><th>职位</th><th>当前角色</th><th>账号状态</th></tr></thead>
        <tbody>
          <tr v-for="user in rows" :key="user.id">
            <td>{{ user.name }}<small>{{ user.username }}</small></td>
            <td>{{ user.departmentName }}</td><td>{{ user.position || '未提供' }}</td>
            <td><span v-for="assigned in user.roles" :key="assigned.id" class="badge" :class="assigned.enabled ? 'blue' : 'red'">{{ assigned.name }}{{ assigned.enabled ? '' : '（角色已停用）' }}</span></td>
            <td><span class="badge" :class="user.enabled ? 'green' : 'red'">{{ user.enabled ? '启用' : '停用' }}</span></td>
          </tr>
          <tr v-if="!rows.length"><td colspan="5" class="empty">{{ busy ? '正在读取员工…' : error ? '名单读取失败' : '暂无匹配员工' }}</td></tr>
        </tbody>
      </table>
    </div>
    <Pagination :page="page" :size="20" :total="total" :busy="busy || applied !== queryKey()" @change="load" />
    <footer class="dialog-actions"><button class="secondary" @click="$emit('close')">关闭</button></footer>
  </Modal>
</template>
