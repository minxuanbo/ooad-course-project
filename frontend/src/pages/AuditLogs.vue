<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { api } from '../api.js'
const filters = ref({ userId: '', username: '', appCode: '', allowed: '', event: '', from: '', to: '', storage: 'ALL' })
const rows = ref([]), cursor = ref(null), more = ref(false), busy = ref(false), error = ref(''), notice = ref(''), before = ref(''), archiveOpen = ref(false)
let alive = true, activeQuery = ''
function query() {
  const params = new URLSearchParams({ limit: '50' })
  for (const [key, value] of Object.entries(filters.value)) if (value !== '') params.set(key, key === 'from' || key === 'to' ? new Date(value).toISOString() : value)
  return params.toString()
}
async function load(append = false) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    if (!append) { activeQuery = query(); rows.value = []; cursor.value = null; more.value = false }
    const page = await api('/authorization-logs?' + activeQuery + (append ? '&cursor=' + encodeURIComponent(cursor.value) : ''))
    if (alive) { rows.value = append ? [...rows.value, ...page.items] : page.items; cursor.value = page.nextCursor; more.value = page.hasMore }
  } catch (e) { if (alive) error.value = e.message + (e.code ? `（${e.code}）` : '') }
  finally { if (alive) busy.value = false }
}
function reset() { filters.value = { userId: '', username: '', appCode: '', allowed: '', event: '', from: '', to: '', storage: 'ALL' }; load() }
async function archive() {
  if (busy.value || !before.value) return
  busy.value = true; error.value = ''; notice.value = ''
  let success = false
  try {
    const result = await api('/authorization-logs/archive', 'POST', { before: new Date(before.value).toISOString(), batchSize: 1000 })
    if (alive) { success = true; notice.value = `本批归档 ${result.archived} 条。` + (result.hasMore ? '仍有符合条件的记录，可继续归档。' : '该截止时间内已完成。'); archiveOpen.value = false }
  } catch (e) { if (alive) error.value = e.message }
  finally { if (alive) { busy.value = false; if (success) await load() } }
}
onMounted(() => load())
onUnmounted(() => { alive = false })
</script>
<template>
  <section class="card panel">
    <h2>鉴权与认证日志</h2><p class="muted">按用户、系统、结果和时间查询。时间输入使用本地时区，记录显示 UTC。归档保留记录及查询能力。</p>
    <form class="audit-filters" @submit.prevent="load()">
      <label>用户 ID<input v-model="filters.userId" type="number" min="1"></label>
      <label>账号（精确匹配）<input v-model="filters.username" maxlength="100"></label>
      <label>系统编码<input v-model="filters.appCode" maxlength="191"></label>
      <label>结果<select v-model="filters.allowed"><option value="">全部</option><option value="true">允许／成功</option><option value="false">拒绝／失败</option></select></label>
      <label>事件<select v-model="filters.event"><option value="">全部</option><option v-for="e in ['AUTHORIZATION','APPLICATION_ENTRY','LOGIN_SUCCESS','LOGIN_FAILED','UNAUTHENTICATED','REQUEST_DENIED']" :key="e">{{ e }}</option></select></label>
      <label>开始（含）<input v-model="filters.from" type="datetime-local"></label><label>结束（不含）<input v-model="filters.to" type="datetime-local"></label>
      <label>记录状态<select v-model="filters.storage"><option value="ALL">全部</option><option value="ACTIVE">未归档</option><option value="ARCHIVED">已归档</option></select></label>
      <button class="primary" :disabled="busy">查询</button><button type="button" class="secondary" :disabled="busy" @click="reset">重置</button>
    </form>
    <p v-if="error" class="error" role="alert">{{ error }}</p><p v-if="notice" role="status">{{ notice }}</p>
    <div class="toolbar"><span>已加载 {{ rows.length }} 条</span><button class="secondary" :disabled="busy" @click="archiveOpen = !archiveOpen">归档历史日志</button></div>
    <form v-if="archiveOpen" class="audit-archive" @submit.prevent="archive">
      <label>截止时间（不含）<input v-model="before" type="datetime-local" required></label><p>确认将此时间前的未归档日志标记为已归档，每批最多 1,000 条。此操作针对全部日志，不受上方筛选限制，不删除数据。</p><button class="primary" :disabled="busy">确认归档本批</button>
    </form>
  </section>
  <section class="card table-scroll"><table><thead><tr><th>UTC 时间</th><th>账号 / 用户 ID</th><th>事件 / 系统</th><th>结果</th><th>原因 / 请求号</th><th>归档</th></tr></thead><tbody>
    <tr v-for="row in rows" :key="row.id"><td>{{ row.occurredAt }}</td><td>{{ row.username || '未识别' }}<small>{{ row.userId }}</small></td><td>{{ row.eventType }}<small>{{ row.appCode || row.requestPath }}</small></td><td><span class="badge" :class="row.allowed ? 'green' : 'red'">{{ row.allowed ? '成功' : '拒绝' }}</span></td><td>{{ row.reason }}<small>{{ row.requestId }}</small></td><td>{{ row.archivedAt ? '已归档' : '未归档' }}</td></tr>
    <tr v-if="!rows.length"><td colspan="6" class="empty">{{ busy ? '正在加载…' : '没有符合条件的日志' }}</td></tr>
  </tbody></table><div class="toolbar"><button v-if="more" class="secondary" :disabled="busy" @click="load(true)">{{ busy ? '加载中…' : '加载更多' }}</button><span v-else-if="rows.length" class="muted">已到本次查询末尾；点击查询获取新记录。</span></div></section>
</template>
<style scoped>
.audit-filters { display: grid; grid-template-columns: repeat(auto-fit,minmax(180px,1fr)); gap: 16px; align-items: end; }
.audit-filters label,.audit-archive label { display: grid; gap: 8px; }
.audit-archive { padding: 20px; border: 1px solid #dce5f2; border-radius: 12px; margin-top: 16px; }
td small { display: block; overflow-wrap: anywhere; color: #64748b; margin-top: 6px; }
</style>
