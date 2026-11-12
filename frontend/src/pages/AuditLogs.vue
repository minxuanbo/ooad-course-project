<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { api } from '../api.js'
const filters = ref({ userId: '', username: '', appCode: '', allowed: '', event: '', from: '', to: '' })
const rows = ref([]), cursor = ref(null), more = ref(false), busy = ref(false), error = ref('')
let alive = true, activeQuery = ''
function query() {
  const params = new URLSearchParams({ limit: '100' })
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
function reset() { filters.value = { userId: '', username: '', appCode: '', allowed: '', event: '', from: '', to: '' }; load() }
onMounted(() => load())
onUnmounted(() => { alive = false })
</script>
<template>
  <section class="card panel">
    <h2>鉴权与认证日志</h2><p class="muted">按用户、系统、结果和时间查询。时间输入使用本地时区，记录显示 UTC。日志仅保留最小记录，无归档或导出。</p>
    <form class="audit-filters" @submit.prevent="load()">
      <label>用户 ID<input v-model="filters.userId" type="number" min="1"></label>
      <label>账号（精确匹配）<input v-model="filters.username" maxlength="100"></label>
      <label>系统编码<input v-model="filters.appCode" maxlength="191"></label>
      <label>结果<select v-model="filters.allowed"><option value="">全部</option><option value="true">允许／成功</option><option value="false">拒绝／失败</option></select></label>
      <label>事件<select v-model="filters.event"><option value="">全部</option><option v-for="e in ['AUTHORIZATION','LOGIN_SUCCESS','LOGIN_FAILED','UNAUTHENTICATED','REQUEST_DENIED']" :key="e">{{ e }}</option></select></label>
      <label>开始（含）<input v-model="filters.from" type="datetime-local"></label><label>结束（不含）<input v-model="filters.to" type="datetime-local"></label>
      <button class="primary" :disabled="busy">查询</button><button type="button" class="secondary" :disabled="busy" @click="reset">重置</button>
    </form>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <div class="toolbar"><span>已加载 {{ rows.length }} 条</span></div>
  </section>
  <section class="card table-scroll"><table><thead><tr><th>UTC 时间</th><th>账号 / 用户 ID</th><th>事件 / 系统</th><th>请求</th><th>结果</th><th>原因 / 请求号</th></tr></thead><tbody>
    <tr v-for="row in rows" :key="row.id"><td>{{ row.occurredAt }}</td><td>{{ row.username || '未识别' }}<small>{{ row.userId }}</small></td><td>{{ row.eventType }}<small>{{ row.appCode || row.permissionCode }}</small></td><td>{{ row.httpMethod }}<small>{{ row.requestPath }}</small></td><td><span class="badge" :class="row.allowed ? 'green' : 'red'">{{ row.allowed ? '成功' : '拒绝' }}</span></td><td>{{ row.reason }}<small>{{ row.requestId }}</small></td></tr>
    <tr v-if="!rows.length"><td colspan="6" class="empty">{{ busy ? '正在加载…' : '没有符合条件的日志' }}</td></tr>
  </tbody></table><div class="toolbar"><button v-if="more" class="secondary" :disabled="busy" @click="load(true)">{{ busy ? '加载中…' : '加载更多' }}</button><span v-else-if="rows.length" class="muted">已到本次查询末尾；点击查询获取新记录。</span></div></section>
</template>
<style scoped>
.audit-filters { display: grid; grid-template-columns: repeat(auto-fit,minmax(180px,1fr)); gap: 16px; align-items: end; }
.audit-filters label { display: grid; gap: 8px; }
td small { display: block; overflow-wrap: anywhere; color: #64748b; margin-top: 6px; }
</style>
