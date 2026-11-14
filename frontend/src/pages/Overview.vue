<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { api, canRoute } from '../api.js'
import { errorText, RequestGeneration } from '../lib/presentation.js'
const result = ref(null),
  error = ref(''),
  busy = ref(false),
  activity = ref([]),
  activityError = ref(''),
  activityBusy = ref(false)
const generation = new RequestGeneration()
const activityGeneration = new RequestGeneration()
/** 主统计与辅助日志分开反馈；日志失败不能阻塞或清除已取得的范围统计。 */
async function load() {
  const version = generation.next()
  activityGeneration.cancel()
  activity.value = []
  activityError.value = ''
  activityBusy.value = false
  busy.value = true
  error.value = ''
  result.value = null
  try {
    const value = await api('/overview')
    if (!generation.current(version)) return
    result.value = value
  } catch (e) {
    if (generation.current(version)) error.value = errorText(e)
  } finally {
    if (generation.current(version)) busy.value = false
  }
  if (generation.current(version) && result.value) await loadActivity()
}
/** 活动单独重试和丢弃晚响应；每次仍检查实际路由及共同根范围。 */
async function loadActivity() {
  if (!canRoute('GET', '/authorization-logs', true)) return
  const version = activityGeneration.next()
  activityBusy.value = true
  activityError.value = ''
  try {
    const logs = await api('/authorization-logs?limit=5')
    if (activityGeneration.current(version)) activity.value = logs.items
  } catch (e) {
    if (activityGeneration.current(version)) activityError.value = errorText(e)
  } finally {
    if (activityGeneration.current(version)) activityBusy.value = false
  }
}
const labels = {
  LOGIN_SUCCESS: '登录成功',
  LOGIN_FAILED: '登录失败',
  UNAUTHENTICATED: '未认证访问',
  AUTHORIZATION: '鉴权判定',
  REQUEST_DENIED: '请求拒绝',
}
onMounted(load)
onUnmounted(() => {
  generation.cancel()
  activityGeneration.cancel()
})
</script>
<template>
  <p v-if="error" class="error card panel" role="alert">
    {{ error }}
    <button class="secondary" @click="load">重试</button>
  </p>
  <p v-if="busy" role="status">正在读取公司概况…</p>
  <template v-if="result">
    <section class="welcome card panel">
      <div class="eyebrow">ORGANIZATION OVERVIEW</div>
      <h2>{{ result.departmentName }}，工作概况</h2>
      <p class="muted">统计包含当前部门及全部下级，停用人员仍计入总数。</p>
    </section>
    <div class="metrics">
      <section
        v-for="item in [
          ['范围内人员', result.totalUsers],
          ['启用账号', result.enabledUsers],
          ['停用账号', result.disabledUsers],
          ['组织节点', result.totalDepartments],
        ]"
        :key="item[0]"
        class="card metric"
      >
        <span>{{ item[0] }}</span>
        <strong>{{ item[1].toLocaleString() }}</strong>
      </section>
    </div>
    <div class="dashboard-columns">
      <section class="card panel">
        <h2>下级组织分布</h2>
        <p>
          本部门直属
          {{
            result.totalUsers - result.distribution.reduce((sum, item) => sum + item.totalUsers, 0)
          }}
          人
        </p>
        <div v-for="item in result.distribution" :key="item.id" class="distribution-row">
          <span>
            {{ item.name }}
            <small>直属 {{ item.directUsers }} 人</small>
          </span>
          <strong>{{ item.totalUsers }} 人</strong>
        </div>
        <p v-if="!result.distribution.length" class="muted">暂无下级组织</p>
      </section>
      <section class="card panel">
        <h2>快捷入口</h2>
        <RouterLink v-if="canRoute('GET', '/users')" class="action-link" to="/users">
          查询与维护人员 →
        </RouterLink>
        <RouterLink v-if="canRoute('GET', '/departments')" class="action-link" to="/departments">
          查看组织部门 →
        </RouterLink>
        <RouterLink v-if="canRoute('GET', '/roles', true)" class="action-link" to="/roles">
          管理角色与权限 →
        </RouterLink>
      </section>
    </div>
    <section v-if="canRoute('GET', '/authorization-logs', true)" class="card panel">
      <h2>最近鉴权与认证活动</h2>
      <p v-if="activityError" class="error" role="alert">
        {{ activityError }}
        <button class="secondary" :disabled="activityBusy" @click="loadActivity">重试活动</button>
      </p>
      <p v-if="activityBusy" role="status">正在读取最近活动…</p>
      <p v-else-if="!activityError && !activity.length" class="muted">暂无活动记录</p>
      <div v-for="row in activity" :key="row.id" class="distribution-row">
        <span>
          {{ labels[row.eventType] || row.eventType }} ·
          {{ row.username || '未识别身份' }}
          <small>{{ row.requestId }}</small>
        </span>
        <time>{{ row.occurredAt }}</time>
      </div>
    </section>
  </template>
</template>
