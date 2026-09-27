<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api.js'
import RoleEditor from '../components/RoleEditor.vue'
const route = useRoute()
const kind = route.path.slice(1), data = ref(null), error = ref(''), editor = ref(null)
const metrics = [['users','组织用户'],['roles','授权角色'],['applications','系统目录'],['permissions','操作权限']]
async function load() {
  try { data.value = await api(kind === 'logs' ? '/authorization-logs?limit=100' : kind === 'apps' ? '/applications' : '/' + kind) }
  catch (e) { error.value = e.message }
}
function saved() { editor.value = null; load() }
onMounted(load)
</script>
<template>
  <p v-if="error" class="error" role="alert">{{ error }}</p><div v-else-if="!data" class="empty">正在加载…</div>
  <template v-else-if="kind === 'dashboard'">
    <div class="welcome"><div><span class="pill">组织访问管理</span><h2>让权限清晰，让管理有序</h2><p>从用户到角色，再到业务功能，在同一处完成授权管理。</p><RouterLink class="primary action-link" to="/users"><span>进入用户授权</span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6"/></svg></RouterLink></div><div class="welcome-symbol" aria-hidden="true">◇</div></div>
    <div class="metrics"><article v-for="m in metrics" :key="m[0]" class="card metric"><div>{{ m[1] }}</div><strong>{{ Number(data[m[0]]).toLocaleString('zh-CN') }}</strong><small>当前数据库统计</small></article></div>
    <section class="card panel"><h2>授权管理流程</h2><div class="step"><b>01</b><div><strong>筛选组织用户</strong><p>按公司、部门及姓名、工号或手机号定位员工。</p></div></div><div class="step"><b>02</b><div><strong>配置角色与权限</strong><p>用户可持有多个角色，有效权限取并集。</p></div></div><div class="step"><b>03</b><div><strong>查看鉴权与离职交接记录</strong><p>业务访问与权限回收均有据可查。</p></div></div><div class="summary-strip">{{ data.departments }} 个部门 · {{ data.authLogs }} 条鉴权记录</div></section>
  </template>
  <div v-else-if="kind === 'roles'" class="role-grid"><article v-for="r in data" :key="r.id" class="card role-card"><span class="role-icon">◇</span><span class="badge" :class="{ green: r.enabled }">{{ r.enabled ? '已启用' : '已停用' }}</span><h2>{{ r.name }}</h2><p class="muted">{{ r.description }}</p><div class="role-counts"><div><strong>{{ r.userCount }}</strong><small>关联用户</small></div><div><strong>{{ r.permissionCount }}</strong><small>操作权限</small></div></div><button class="secondary" @click="editor = r">配置角色权限 →</button></article></div>
  <div v-else-if="kind === 'apps'" class="app-grid"><article v-for="a in data" :key="a.id" class="card app-card"><span class="app-icon">{{ a.name[0] }}</span><span class="badge" :class="{ green: a.enabled }">{{ a.enabled ? '已启用' : '已停用' }}</span><h2>{{ a.name }}</h2><p>{{ a.permissionCount }} 项操作权限</p><p class="muted">暂未接入</p><code>{{ a.code }}</code></article></div>
  <section v-else class="card"><div class="toolbar"><strong>最近鉴权记录</strong><span class="muted">最近 100 条 · 时间为 UTC</span></div><div class="table-scroll"><table><thead><tr><th>鉴权时间</th><th>用户账号</th><th>目标系统</th><th>权限编码</th><th>结果</th><th>原因</th></tr></thead><tbody><tr v-for="l in data" :key="l.id"><td class="nowrap">{{ l.occurredAt }}</td><td>{{ l.username }}</td><td>{{ l.appCode }}</td><td class="code-cell">{{ l.permissionCode }}</td><td><span class="badge" :class="l.allowed ? 'green' : 'red'">{{ l.allowed ? '允许' : '拒绝' }}</span></td><td>{{ l.reason }}</td></tr><tr v-if="!data.length"><td colspan="6" class="empty">暂无鉴权记录</td></tr></tbody></table></div></section>
  <RoleEditor v-if="editor" kind="roles" :target="editor" @close="editor = null" @saved="saved" />
</template>
