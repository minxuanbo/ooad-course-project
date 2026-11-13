<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { api } from '../api.js'
import Modal from '../components/Modal.vue'
const filters = ref({ userId: '', username: '', appCode: '', allowed: '', event: '', from: '', to: '' })
const rows = ref([]), cursor = ref(null), more = ref(false), busy = ref(false), error = ref(''), limit = ref(20), total = ref(0), detail = ref(null)
let alive = true, activeQuery = '', generation = 0
const eventLabels = { LOGIN_SUCCESS: '登录成功', LOGIN_FAILED: '登录失败', UNAUTHENTICATED: '未认证访问', AUTHORIZATION: '鉴权判定', REQUEST_DENIED: '请求拒绝' }
/** 查询指纹包含每批条数，日期统一转换UTC提交。 */
function query() {
  const params = new URLSearchParams({ limit: String(limit.value) })
  for (const [key, value] of Object.entries(filters.value)) if (value !== '') params.set(key, key === 'from' || key === 'to' ? new Date(value).toISOString() : value)
  return params.toString()
}
function dirty() { try { return activeQuery !== query() } catch { return true } }
/** 续批仅能使用相同筛选及条数的已完成快照，晚响应不会消除待查询状态。 */
async function load(append = false) {
  if (busy.value || (append && (dirty() || !more.value))) return
  const version = ++generation
  busy.value = true; error.value = ''
  try {
    if (filters.value.from && filters.value.to && new Date(filters.value.from) >= new Date(filters.value.to)) throw Error('开始时间必须早于结束时间。')
    if (!append) { activeQuery = query(); rows.value = []; cursor.value = null; more.value = false; total.value = 0 }
    const page = await api('/authorization-logs?' + activeQuery + (append ? '&cursor=' + encodeURIComponent(cursor.value) : ''))
    if (alive && version === generation) {
      rows.value = append ? [...rows.value, ...page.items] : page.items
      cursor.value = page.nextCursor; more.value = page.hasMore; total.value = page.total
    }
  } catch (e) { if (alive && version === generation) error.value = e.message + (e.code ? `（${e.code}）` : '') + (e.requestId ? ` · 请求号 ${e.requestId}` : '') }
  finally { if (alive && version === generation) busy.value = false }
}
function reset() { filters.value = { userId: '', username: '', appCode: '', allowed: '', event: '', from: '', to: '' }; load() }
onMounted(() => load())
onUnmounted(() => { alive = false; generation++; rows.value = [] })
</script>
<template>
  <section class="card panel">
    <h2>鉴权与认证日志</h2><p class="muted">鉴权允许与业务处理结果分别记录。查询不读取部门树；匹配总数固定于本次查询快照。</p>
    <form class="audit-filters" @submit.prevent="load()">
      <label>用户 ID<input v-model="filters.userId" type="number" min="1"></label>
      <label>账号（精确匹配）<input v-model="filters.username" maxlength="100"></label>
      <label>系统编码<input v-model="filters.appCode" maxlength="191"></label>
      <label>判定<select v-model="filters.allowed"><option value="">全部</option><option value="true">允许 / 成功</option><option value="false">拒绝 / 失败</option></select></label>
      <label>事件<select v-model="filters.event"><option value="">全部</option><option v-for="(label, value) in eventLabels" :key="value" :value="value">{{ label }}</option></select></label>
      <label>开始（含）<input v-model="filters.from" type="datetime-local"></label><label>结束（不含）<input v-model="filters.to" type="datetime-local"></label>
      <label>每批条数<select v-model="limit"><option v-for="value in [10,20,50,100]" :key="value" :value="value">{{ value }} 条</option></select></label>
      <button class="primary" :disabled="busy">{{ busy ? '查询中…' : '查询' }}</button><button type="button" class="secondary" :disabled="busy" @click="reset">重置</button>
    </form>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="dirty()" class="pending-query" role="status">筛选条件已改变，请重新查询；当前不能续查旧快照。</p>
    <div class="toolbar"><span>匹配 {{ total }} 条 · 已加载 {{ rows.length }} 条</span></div>
  </section>
  <section class="card table-scroll"><table><thead><tr><th>UTC 时间</th><th>账号 / 用户 ID</th><th>事件 / 系统</th><th>请求</th><th>鉴权判定 / HTTP</th><th>原因 / 详情</th></tr></thead><tbody>
    <tr v-for="row in rows" :key="row.id"><td>{{ row.occurredAt }}</td><td>{{ row.username || '未识别身份' }}<small>{{ row.userId || '无已识别用户' }}</small></td><td>{{ eventLabels[row.eventType] || row.eventType }}<small>{{ row.appCode || row.permissionCode }}</small></td><td>{{ row.httpMethod }}<small>{{ row.requestPath }}</small></td><td><span class="badge" :class="row.allowed ? 'green' : 'red'">{{ row.allowed ? '允许 / 成功' : '拒绝 / 失败' }}</span><small>HTTP {{ row.httpStatus }}</small></td><td>{{ row.reason }}<button class="text-button" @click="detail = row">详情</button></td></tr>
    <tr v-if="!rows.length"><td colspan="6" class="empty">{{ busy ? '正在加载…' : error ? '日志读取失败' : '没有符合条件的日志' }}</td></tr>
  </tbody></table><div class="toolbar"><button v-if="more" class="secondary" :disabled="busy || dirty()" @click="load(true)">{{ busy ? '加载中…' : '加载下一批' }}</button><span v-else-if="rows.length" class="muted">已到本次查询末尾；点击查询获取新记录。</span></div></section>
  <Modal v-if="detail" title="日志详情" @close="detail = null"><dl class="log-detail"><template v-for="(value, key) in detail" :key="key"><dt>{{ key }}</dt><dd>{{ value ?? '—' }}</dd></template></dl><button class="secondary" @click="detail = null">关闭</button></Modal>
</template>
<style scoped>
.audit-filters { display: grid; grid-template-columns: repeat(auto-fit,minmax(170px,1fr)); gap: 16px; align-items: end; }
.audit-filters label { display: grid; gap: 8px; }
td small { display: block; overflow-wrap: anywhere; color: #64748b; margin-top: 6px; }
</style>
