<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { api } from '../api.js'
import Modal from '../components/Modal.vue'
import PageActions from '../components/PageActions.vue'
import AppIcon from '../components/AppIcon.vue'
import { formatBeijingTime, beijingInputToUtc } from '../lib/presentation.js'
import { authorizationResultLabel } from '../lib/presentation.js'
const filters = ref({
  userId: '',
  username: '',
  appCode: '',
  allowed: '',
  event: '',
  from: '',
  to: '',
})
const rows = ref([]),
  systems = ref([]),
  cursor = ref(null),
  more = ref(false),
  busy = ref(false),
  error = ref(''),
  limit = ref(12),
  total = ref(0),
  snapshotId = ref(null),
  detail = ref(null)
let alive = true,
  activeQuery = '',
  generation = 0
const eventLabels = {
  LOGIN_SUCCESS: '身份认证成功',
  LOGIN_FAILED: '身份认证失败',
  UNAUTHENTICATED: '未认证访问',
  AUTHORIZATION: '鉴权判定',
  PERMISSION_CHECK: '权限查询',
  REQUEST_DENIED: '请求拒绝',
}
const detailFields = [
  ['事件ID', 'id'],
  ['时间（北京时间）', 'occurredAt'],
  ['UTC原值', 'utc'],
  ['事件类型', 'eventType'],
  ['用户ID', 'userId'],
  ['工号', 'username'],
  ['系统', 'appCode'],
  ['请求来源 IP', 'clientIp'],
  ['权限编码', 'permissionCode'],
  ['HTTP方法', 'httpMethod'],
  ['安全路径', 'requestPath'],
  ['请求标识', 'requestId'],
]
/** 应用名称来自日志查询返回的登记目录；旧记录仍保留稳定编码用于定位。 */
function systemName(code) {
  return systems.value.find((item) => item.code === code)?.name || code
}
/** 查询指纹包含每批条数，输入按北京时间解释后转换UTC提交。 */
function query() {
  const params = new URLSearchParams({ limit: String(limit.value) })
  for (const [key, value] of Object.entries(filters.value))
    if (value !== '')
      params.set(key, key === 'from' || key === 'to' ? beijingInputToUtc(value) : value)
  return params.toString()
}
function dirty() {
  try {
    return activeQuery !== query()
  } catch {
    return true
  }
}
/** 续批仅能使用相同筛选及条数的已完成快照，晚响应不会消除待查询状态。 */
async function load(append = false) {
  if (busy.value || (append && (dirty() || !more.value))) return
  const version = ++generation
  busy.value = true
  error.value = ''
  try {
    if (
      filters.value.from &&
      filters.value.to &&
      new Date(beijingInputToUtc(filters.value.from)) >=
        new Date(beijingInputToUtc(filters.value.to))
    )
      throw Error('开始时间必须早于结束时间。')
    if (!append) {
      activeQuery = query()
      rows.value = []
      cursor.value = null
      more.value = false
      total.value = 0
      snapshotId.value = null
    }
    const page = await api(
      '/authorization-logs?' +
        activeQuery +
        (append ? '&cursor=' + encodeURIComponent(cursor.value) : ''),
    )
    if (alive && version === generation) {
      rows.value = append ? [...rows.value, ...page.items] : page.items
      cursor.value = page.nextCursor
      more.value = page.hasMore
      total.value = page.total
      snapshotId.value = page.snapshotId
      systems.value = page.systems || []
    }
  } catch (e) {
    if (alive && version === generation)
      error.value =
        e.message +
        (e.code ? `（${e.code}）` : '') +
        (e.requestId ? ` · 请求号 ${e.requestId}` : '')
  } finally {
    if (alive && version === generation) busy.value = false
  }
}
function reset() {
  filters.value = {
    userId: '',
    username: '',
    appCode: '',
    allowed: '',
    event: '',
    from: '',
    to: '',
  }
  load()
}
onMounted(() => load())
onUnmounted(() => {
  alive = false
  generation++
  rows.value = []
})
</script>
<template>
  <PageActions :busy="busy" @refresh="load(false)" />
  <section class="card panel">
    <form class="audit-filters" @submit.prevent="load()">
      <label>工号<input v-model="filters.username" maxlength="100" placeholder="精确工号" /></label>
      <label>
        用户ID
        <input v-model="filters.userId" type="number" min="1" placeholder="内部用户ID" />
      </label>
      <label
        >鉴权结果<select v-model="filters.allowed">
          <option value="">全部结果</option>
          <option value="true">允许 / 有权限 / 认证成功</option>
          <option value="false">拒绝 / 无权限 / 认证失败</option>
        </select></label
      >
      <label
        >事件类型<select v-model="filters.event">
          <option value="">全部事件</option>
          <option v-for="(label, value) in eventLabels" :key="value" :value="value">
            {{ label }}
          </option>
        </select></label
      >
      <label>
        系统
        <select v-model="filters.appCode">
          <option value="">全部系统</option>
          <option v-for="system in systems" :key="system.id" :value="system.code">
            {{ system.name }}{{ system.enabled ? '' : '（已停用）' }}{{ system.connectionStatus === 'PENDING' ? '（待接入）' : '' }} · {{ system.code }}
          </option>
          <option
            v-if="filters.appCode && !systems.some((item) => item.code === filters.appCode)"
            :value="filters.appCode"
          >
            {{ filters.appCode }}（历史记录）
          </option>
        </select>
      </label>
      <label>
        开始（北京时间，含）
        <input v-model="filters.from" type="datetime-local" />
      </label>
      <label>
        结束（北京时间，不含）
        <input v-model="filters.to" type="datetime-local" />
      </label>
      <label>
        每批条数
        <select v-model="limit">
          <option v-for="value in [6, 12, 24, 50, 100]" :key="value" :value="value">
            {{ value }} 条
          </option>
        </select>
      </label>
      <div class="audit-filter-actions">
        <button class="primary" :disabled="busy">{{ busy ? '查询中…' : '查询' }}</button
        ><button type="button" class="text-button" :disabled="busy" @click="reset">重置</button>
      </div>
    </form>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="dirty()" class="pending-query" role="status">
      筛选条件已改变，请重新查询；当前不能续查旧快照。
    </p>
    <div class="audit-summary">
      <span
        >匹配总数 <strong>{{ total }}</strong></span
      ><span
        >已加载 <strong>{{ rows.length }}</strong> 条</span
      ><span class="audit-snapshot"
        >{{ dirty() ? '筛选已修改，请点击查询' : '同一查询快照'
        }}{{ !dirty() && snapshotId != null ? ' · #' + snapshotId : '' }}</span
      >
    </div>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>事件时间</th>
            <th>操作主体</th>
            <th>事件类型</th>
            <th>鉴权结果</th>
            <th>访问目标</th>
            <th>原因 / 响应</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>
              <time :datetime="row.occurredAt" :title="row.occurredAt">
                {{ formatBeijingTime(row.occurredAt) }}
              </time>
              <small>北京时间</small>
            </td>
            <td>
              {{ row.username || '未识别身份' }}
              <small>{{ row.userId ? '用户ID ' + row.userId : '无已识别用户' }}</small>
            </td>
            <td>
              <span class="audit-event">{{ eventLabels[row.eventType] || row.eventType }}</span>
              <small>{{ systemName(row.appCode) }} · {{ row.appCode || row.permissionCode }}</small>
            </td>
            <td>
              <span class="audit-result" :class="{ denied: !row.allowed }">{{
                authorizationResultLabel(row)
              }}</span>
            </td>
            <td class="mono">
              {{ row.httpMethod }}
              <small>{{ row.requestPath }}</small>
            </td>
            <td class="mono">
              {{ row.reason }}
              <small>HTTP {{ row.httpStatus }}</small>
            </td>
            <td><button class="text-button" @click="detail = row">查看详情</button></td>
          </tr>
          <tr v-if="!rows.length">
            <td colspan="7" class="empty">
              {{ busy ? '正在加载…' : error ? '日志读取失败' : '没有符合条件的日志' }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="audit-load-more">
      <button v-if="more" class="secondary" :disabled="busy || dirty()" @click="load(true)">
        {{ busy ? '加载中…' : '加载下一批' }}
      </button>
      <span v-else-if="rows.length" class="muted">已加载全部匹配记录</span
      ><span class="muted">刷新后才纳入新事件</span>
    </div>
  </section>
  <Modal v-if="detail" title="日志详情" drawer @close="detail = null">
    <div class="audit-detail-result" :class="{ denied: !detail.allowed }">
      <AppIcon name="shield" />
      <div>
        <strong>{{ authorizationResultLabel(detail) }}</strong>
        <p>HTTP {{ detail.httpStatus }} · {{ detail.reason }}</p>
      </div>
    </div>
    <dl class="log-detail">
      <template v-for="[label, key] in detailFields" :key="key">
        <dt>{{ label }}</dt>
        <dd :title="key === 'occurredAt' ? detail.occurredAt : undefined">
          {{
            key === 'occurredAt'
              ? formatBeijingTime(detail.occurredAt)
              : key === 'utc'
                ? detail.occurredAt
                : (detail[key] ?? (key === 'clientIp' ? '未采集' : '—'))
          }}
        </dd>
      </template>
    </dl>
    <button class="secondary" @click="detail = null">关闭</button>
  </Modal>
</template>
<style scoped>
.panel {
  padding: 0;
}
.audit-filters {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px 14px;
  align-items: end;
  padding: 22px;
}
.audit-filters label {
  display: grid;
  gap: 8px;
  color: #97a6bd;
  font-size: 11px;
  min-width: 0;
}
.audit-filters input,
.audit-filters select {
  width: 100%;
  height: 38px;
  padding: 8px 12px;
  background: white;
  border: 1px solid #e1e7f1;
  font-size: 12px;
}
.audit-filter-actions {
  grid-column: 1 / -1;
  display: flex;
  gap: 18px;
}
.audit-filter-actions button {
  min-height: 38px;
  font-size: 12px;
}
.audit-summary {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 15px 22px;
  border-top: 1px solid #f0f3f8;
  background: #fbfcff;
  color: #94a7c6;
  font-size: 11px;
}
.audit-summary strong {
  margin: 0 4px;
  color: #7a9aca;
  font-size: 14px;
  font-weight: 550;
}
.audit-snapshot {
  margin-left: auto;
  font-size: 10px;
  color: #adbbd0;
}
.table-scroll {
  margin-top: 0;
}
table {
  min-width: 960px;
}
th {
  background: #f7f9fc;
  font-size: 11px;
  color: #98a6bb;
  padding: 13px 22px;
}
td {
  font-size: 11px;
  color: #7d94b5;
  padding: 16px 22px;
}
td small {
  display: block;
  overflow-wrap: anywhere;
  color: #a8b9d2;
  margin-top: 6px;
  font-size: 10px;
}
td time {
  white-space: nowrap;
}
td .text-button {
  padding: 2px;
  font-size: 12px;
  white-space: nowrap;
}
td .mono,
td.mono {
  font-size: 11px;
}
.audit-event {
  display: inline-block;
  padding: 3px 7px;
  background: #f0f3f8;
  color: #94a2b7;
  border-radius: 4px;
  white-space: nowrap;
}
.audit-result {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #28a185;
  white-space: nowrap;
}
.audit-result::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}
.audit-result.denied {
  color: #bd9660;
}
.audit-load-more {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px;
  color: #9aacc6;
  font-size: 11px;
  flex-wrap: wrap;
}
.pending-query,
.error {
  margin: 0 22px 16px;
}
.audit-detail-result {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  border: 1px solid #dcefe8;
  background: #f3fbf8;
  color: #479a81;
  border-radius: 8px;
  margin-bottom: 24px;
}
.audit-detail-result.denied {
  background: #fff9ef;
  border-color: #f0e5d2;
  color: #b59463;
}
.audit-detail-result :deep(.app-icon) {
  width: 28px;
  height: 28px;
}
.audit-detail-result p {
  font-size: 12px;
  margin: 6px 0 0;
}
@media (max-width: 1000px) {
  .audit-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 480px) {
  .audit-filters {
    padding: 18px 14px;
    gap: 16px 10px;
  }
  .audit-summary {
    padding: 15px 14px;
    flex-wrap: wrap;
    gap: 12px;
  }
  .audit-snapshot {
    margin-left: 0;
    flex-basis: 100%;
  }
}
</style>
