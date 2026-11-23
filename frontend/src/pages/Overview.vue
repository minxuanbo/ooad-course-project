<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { api, canRoute, session } from '../api.js'
import { errorText, RequestGeneration, formatBeijingTime } from '../lib/presentation.js'
import { authorizationResultLabel } from '../lib/presentation.js'
import { resourcePageAvailable } from '../lib/presentation.js'
import AppIcon from '../components/AppIcon.vue'
import PageActions from '../components/PageActions.vue'
import MemberList from '../components/MemberList.vue'
const members = ref(null)
const result = ref(null),
  error = ref(''),
  busy = ref(false),
  activity = ref([]),
  activityError = ref(''),
  activityBusy = ref(false)
const generation = new RequestGeneration()
const activityGeneration = new RequestGeneration()
const metrics = computed(() =>
  result.value
    ? [
        {
          label: '范围内人员',
          value: result.value.totalUsers,
          icon: 'users',
          unit: '人',
          note: '包含本部门及下级',
        },
        {
          label: '启用账号',
          value: result.value.enabledUsers,
          icon: 'user',
          unit: '人',
          note: '可用状态不等于拥有管理权限',
        },
        {
          label: '组织节点',
          value: result.value.totalDepartments,
          icon: 'building',
          unit: '个',
          note: '公司、部门及项目节点',
        },
        {
          label: '停用账号',
          value: result.value.disabledUsers,
          icon: 'ban',
          unit: '人',
          note: '角色与部门归属仍保留',
        },
      ]
    : [],
)
const maximum = computed(() =>
  Math.max(1, ...(result.value?.distribution || []).map((item) => item.totalUsers)),
)
const shortcuts = computed(() =>
  [
    { path: '/departments', capability: '/departments', name: '部门管理', icon: 'building' },
    { path: '/users', capability: '/users', name: '人员管理', icon: 'users' },
    { path: '/roles', capability: '/roles', name: '角色与权限', icon: 'shield' },
    {
      path: '/resources',
      resource: true,
      name: '资源与API',
      icon: 'resource',
      global: true,
    },
    {
      path: '/logs',
      capability: '/authorization-logs',
      name: '鉴权日志',
      icon: 'history',
      global: true,
    },
  ].filter((item) =>
    item.resource ? resourcePageAvailable(canRoute) : canRoute('GET', item.capability, item.global),
  ),
)
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
  LOGIN_SUCCESS: '身份认证成功',
  LOGIN_FAILED: '身份认证失败',
  UNAUTHENTICATED: '未认证访问',
  AUTHORIZATION: '鉴权判定',
  PERMISSION_CHECK: '权限查询',
  REQUEST_DENIED: '请求拒绝',
}
onMounted(load)
onUnmounted(() => {
  generation.cancel()
  activityGeneration.cancel()
})
</script>
<template>
  <PageActions :busy="busy" @refresh="load" />
  <p v-if="error" class="error card panel" role="alert">
    {{ error }}
    <button class="secondary" @click="load">重试</button>
  </p>
  <p v-if="busy" role="status">正在读取公司概况…</p>
  <template v-if="result">
    <section class="overview-welcome">
      <div>
        <span class="overview-eyebrow">WORKSPACE OVERVIEW</span>
        <h2>{{ session.me.user.name }}，欢迎回来</h2>
        <p>从组织到授权，让每一项管理都有清晰的边界。</p>
        <span class="overview-scope"
          ><AppIcon name="building" />{{ result.departmentName }}及下级</span
        >
      </div>
      <div class="overview-orbit" aria-hidden="true">
        <span><AppIcon name="shield" /></span><i></i><i></i>
      </div>
    </section>
    <div class="metrics">
      <section v-for="(item, index) in metrics" :key="item.label" class="card overview-metric">
        <div>
          <span>{{ item.label }}</span
          ><span class="overview-metric-icon" :class="'tone-' + index"
            ><AppIcon :name="item.icon"
          /></span>
        </div>
        <strong
          >{{ item.value.toLocaleString() }}<small>{{ item.unit }}</small></strong
        >
        <p>{{ item.note }}</p>
      </section>
    </div>
    <div class="overview-columns">
      <section class="card overview-distribution">
        <div class="overview-section-heading">
          <h2>{{ session.me.user.globalScope ? '公司组织概况' : '管理范围概况' }}</h2>
          <span class="badge blue">{{ result.departmentName }}</span>
        </div>
        <div class="overview-distribution-body">
          <p class="overview-hint">下级组织人员分布 · 点击查看对应人员</p>
          <button
            v-for="item in result.distribution"
            :key="item.id"
            class="overview-distribution-row"
            :disabled="!canRoute('GET', '/users')"
            @click="members = item"
          >
            <span>{{ item.name }}</span
            ><span class="overview-bar"
              ><i
                :style="{ width: Math.max(3, (item.totalUsers / maximum) * 100) + '%' }"
              ></i></span
            ><strong>{{ item.totalUsers }}<small>人</small></strong
            ><AppIcon name="chevron" />
          </button>
          <div v-if="!result.distribution.length" class="overview-leaf">
            <AppIcon name="project" />
            <h3>当前已是末级组织</h3>
            <p>当前范围共 {{ result.totalUsers }} 名直属人员。</p>
          </div>
          <div class="overview-distribution-foot">
            本部门直属
            <strong>{{
              result.totalUsers -
              result.distribution.reduce((sum, item) => sum + item.totalUsers, 0)
            }}</strong>
            人 · 人员总数包含全部下级
          </div>
        </div>
      </section>
      <section class="card overview-shortcuts">
        <div class="overview-section-heading">
          <h2>快捷入口</h2>
          <span class="overview-hint">按当前权限展示</span>
        </div>
        <div class="overview-shortcut-grid">
          <RouterLink v-for="item in shortcuts" :key="item.path" :to="item.path"
            ><span><AppIcon :name="item.icon" /></span><strong>{{ item.name }}</strong
            ><AppIcon name="chevron"
          /></RouterLink>
        </div>
        <div class="overview-tip">
          <AppIcon name="shield" />
          <div>
            <strong>权限与岗位各司其职</strong>
            <p>职位为只读人事资料。角色决定系统能力，部门限定管理范围。</p>
          </div>
        </div>
      </section>
    </div>
    <section v-if="canRoute('GET', '/authorization-logs', true)" class="card overview-activity">
      <div class="overview-section-heading">
        <h2>最近鉴权活动</h2>
        <RouterLink to="/logs">查看日志 ›</RouterLink>
      </div>
      <p v-if="activityError" class="error" role="alert">
        {{ activityError }}
        <button class="secondary" :disabled="activityBusy" @click="loadActivity">重试活动</button>
      </p>
      <p v-if="activityBusy" role="status">正在读取最近活动…</p>
      <p v-else-if="!activityError && !activity.length" class="muted">暂无活动记录</p>
      <div v-for="row in activity" :key="row.id" class="overview-activity-row">
        <span class="activity-dot" :class="{ denied: !row.allowed }"></span
        ><span class="activity-subject"
          >{{ row.username || '未识别身份'
          }}<small
            >{{ labels[row.eventType] || row.eventType }} · {{ row.httpMethod }}
            {{ row.requestPath }}</small
          ></span
        ><span class="badge" :class="row.allowed ? 'blue' : 'amber'">{{
          authorizationResultLabel(row)
        }}</span>
        <time :datetime="row.occurredAt" :title="row.occurredAt">
          {{ formatBeijingTime(row.occurredAt) }}
        </time>
      </div>
    </section>
  </template>
  <MemberList
    v-if="members"
    :key="members.id"
    :department="members"
    initial-scope="SUBTREE"
    @close="members = null"
  />
</template>
<style scoped>
.overview-welcome {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 36px 32px;
  border: 1px solid #e2ebfc;
  background: linear-gradient(110deg, #f0f5ff, #f9fbff 72%);
  border-radius: 12px;
  margin-bottom: 22px;
  overflow: hidden;
}
.overview-eyebrow {
  font-size: 11px;
  color: #3865ef;
  letter-spacing: 1.6px;
  font-weight: 650;
}
.overview-welcome h2 {
  font-size: 24px;
  color: #2c4771;
  margin: 13px 0 8px;
}
.overview-welcome p {
  font-size: 12px;
  color: #91a3c0;
  margin: 0 0 18px;
}
.overview-scope {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border: 1px solid #e2ebfc;
  border-radius: 5px;
  color: #7d9bc9;
  background: #ffffffb3;
  font-size: 11px;
}
.overview-scope :deep(.ui-icon) {
  width: 12px;
  height: 12px;
}
.overview-orbit {
  width: 240px;
  height: 150px;
  flex: 0 0 240px;
  position: relative;
  display: grid;
  place-items: center;
}
.overview-orbit > span {
  width: 66px;
  height: 66px;
  display: grid;
  place-items: center;
  border-radius: 22px;
  background: #eaf2ff;
  color: #93b4ed;
}
.overview-orbit > span :deep(.ui-icon) {
  width: 34px;
  height: 34px;
}
.overview-orbit i {
  position: absolute;
  width: 210px;
  height: 124px;
  border: 1px solid #dfebff;
  border-radius: 50%;
  transform: rotate(29deg);
}
.overview-orbit i:last-child {
  width: 140px;
  height: 92px;
  transform: rotate(-20deg);
}
.metrics {
  gap: 16px;
  margin-bottom: 22px;
}
.overview-metric {
  padding: 20px 23px;
}
.overview-metric > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #91a0b7;
}
.overview-metric-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: #edf3ff;
  color: #8aa9dd;
}
.overview-metric-icon.tone-1 {
  background: #eaf6f1;
  color: #7eaf9e;
}
.overview-metric-icon.tone-2 {
  background: #f0edfb;
  color: #ab9bd1;
}
.overview-metric-icon.tone-3 {
  background: #fff4e8;
  color: #cfaa7a;
}
.overview-metric strong {
  font-size: 30px;
  color: #385477;
  font-weight: 600;
  display: block;
  margin-top: 14px;
  line-height: 1.3;
}
.overview-metric strong small {
  display: inline;
  font-size: 11px;
  margin-left: 8px;
  font-weight: 400;
  color: #9daec8;
}
.overview-metric p {
  font-size: 10px;
  color: #9dadc5;
  margin: 13px 0 0;
}
.overview-columns {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 24px;
  margin-bottom: 24px;
}
.overview-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 22px 16px;
}
.overview-section-heading h2 {
  font-size: 15px;
  color: #385476;
  margin: 0;
}
.overview-section-heading .badge {
  margin: 0;
  font-size: 10px;
}
.overview-section-heading a {
  color: #7897c6;
  font-size: 11px;
  text-decoration: none;
}
.overview-hint {
  color: #9baac1;
  font-size: 11px;
}
.overview-distribution-body {
  padding: 0 22px 18px;
}
.overview-distribution-row {
  display: grid;
  grid-template-columns: 100px minmax(24px, 1fr) 44px 12px;
  gap: 14px;
  align-items: center;
  width: 100%;
  padding: 13px 0;
  text-align: left;
  background: transparent;
  color: #7991b1;
  font-size: 11px;
}
.overview-distribution-row:hover {
  color: #3865ef;
}
.overview-bar {
  height: 6px;
  border-radius: 4px;
  background: #f0f3f9;
  overflow: hidden;
}
.overview-bar i {
  display: block;
  height: 100%;
  background: #87a9ed;
  border-radius: 4px;
}
.overview-distribution-row strong {
  font-size: 11px;
  font-weight: 550;
  text-align: right;
}
.overview-distribution-row strong small {
  display: inline;
  margin-left: 5px;
  font-size: 10px;
}
.overview-distribution-row :deep(.ui-icon) {
  width: 11px;
  height: 11px;
  color: #b1c4df;
}
.overview-distribution-foot {
  margin-top: 12px;
  border-top: 1px solid #ecf0f7;
  padding-top: 17px;
  font-size: 10px;
  color: #9aaac0;
}
.overview-shortcut-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 9px;
  padding: 0 20px;
}
.overview-shortcut-grid a {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid #edf1f8;
  padding: 11px 10px;
  border-radius: 8px;
  color: #7893ba;
  text-decoration: none;
}
.overview-shortcut-grid a > span {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: #f1f5ff;
  color: #8faedd;
}
.overview-shortcut-grid a strong {
  flex: 1;
  font-size: 12px;
  font-weight: 400;
}
.overview-shortcut-grid a > :deep(.ui-icon) {
  width: 11px;
  height: 11px;
  color: #b6c9e4;
}
.overview-tip {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin: 20px;
  padding: 16px;
  background: #f7faff;
  color: #91aed7;
  border-radius: 6px;
}
.overview-tip :deep(.ui-icon) {
  width: 15px;
  height: 15px;
  margin-top: 2px;
}
.overview-tip strong {
  font-size: 12px;
  font-weight: 400;
}
.overview-tip p {
  font-size: 10px;
  color: #a1b2cc;
  margin: 6px 0 0;
}
.overview-leaf {
  padding: 25px 10px;
  text-align: center;
  color: #91a5c4;
}
.overview-leaf h3 {
  font-size: 13px;
  font-weight: 500;
}
.overview-leaf p {
  font-size: 11px;
}
.overview-activity-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 22px;
  border-top: 1px solid #f0f3f8;
  color: #6c86aa;
  font-size: 12px;
}
.activity-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #74baa1;
  flex-shrink: 0;
}
.activity-dot.denied {
  background: #d5aa73;
}
.activity-subject {
  flex: 1;
  min-width: 0;
}
.activity-subject small {
  margin-top: 5px;
  font-size: 10px;
  overflow-wrap: anywhere;
  color: #a1b0c5;
}
.overview-activity-row time {
  font-size: 10px;
  white-space: nowrap;
  color: #9eacc1;
}
.overview-activity > p {
  padding: 0 22px;
}
@media (max-width: 1100px) {
  .overview-columns {
    gap: 16px;
  }
  .overview-shortcut-grid {
    grid-template-columns: 1fr;
  }
  .overview-distribution-row {
    grid-template-columns: 85px minmax(24px, 1fr) 38px 10px;
    gap: 8px;
  }
}
@media (max-width: 780px) {
  .overview-columns {
    grid-template-columns: 1fr;
  }
  .overview-shortcut-grid {
    grid-template-columns: 1fr 1fr;
  }
  .overview-orbit {
    display: none;
  }
  .overview-welcome {
    padding: 26px;
  }
  .overview-welcome h2 {
    font-size: 21px;
  }
  .overview-activity-row time {
    display: none;
  }
}
@media (max-width: 480px) {
  .overview-metric {
    padding: 16px;
  }
  .overview-metric strong {
    font-size: 26px;
  }
  .overview-metric > div {
    font-size: 11px;
  }
  .overview-metric p {
    min-height: 30px;
  }
}
</style>
